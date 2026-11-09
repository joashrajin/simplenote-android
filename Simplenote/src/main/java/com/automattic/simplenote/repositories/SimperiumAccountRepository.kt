package com.automattic.simplenote.repositories

import android.content.Context
import androidx.preference.PreferenceManager
import com.automattic.simplenote.Simplenote.SCROLL_POSITION_PREFERENCES
import com.automattic.simplenote.Simplenote.SYNC_TIME_PREFERENCES
import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.di.MainDispatcher
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.utils.AppLog
import com.automattic.simplenote.utils.AppLog.Type
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.WordPressTokenStore
import com.simperium.Simperium
import com.simperium.android.AndroidClient
import com.simperium.android.AsyncAuthClient.USER_ACCESS_TOKEN_PREFERENCE
import com.simperium.android.AsyncAuthClient.USER_EMAIL_PREFERENCE
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import com.simperium.client.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SimperiumAccountRepository @Inject constructor(
    private val simperium: Simperium,
    private val accountBucket: Bucket<Account>,
    private val notesBucket: Bucket<Note>,
    private val tagsBucket: Bucket<Tag>,
    private val preferencesBucket: Bucket<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
) : AccountRepository {

    override fun session(): UserSession {
        val currentUser = simperium.user ?: return UserSession.UnauthorizedUser

        return when (currentUser.email != null && !currentUser.needsAuthorization()) {
            true -> UserSession.AuthorizedUser(currentUser)
            false -> UserSession.UnauthorizedUser
        }
    }

    override fun isLoggedIn(): Boolean {
        val user = simperium.user
        return user != null && user.status == User.Status.AUTHORIZED
    }

    override fun userEmail(): String? = simperium.user?.email

    // NonCancellable + main: every legacy caller ran this synchronously on the main thread, so
    // entered there via Main.immediate it completes inline, and a late cancellation can never
    // leave the Simperium user authorized without a persisted session (or vice versa).
    override suspend fun loginWithToken(email: String?, token: String?) =
        withContext(NonCancellable + mainDispatcher) {
            // Manually authorize the user with Simperium
            val user = simperium.user
            user.accessToken = token
            user.email = email
            user.status = User.Status.AUTHORIZED

            // Store the user data in Simperium shared preferences
            AndroidClient.sharedPreferences(context).edit()
                .putString(USER_ACCESS_TOKEN_PREFERENCE, user.accessToken)
                .putString(USER_EMAIL_PREFERENCE, user.email)
                .apply()
        }

    override suspend fun logout() = withContext(NonCancellable + mainDispatcher) {
        simperium.deauthorizeUser()

        accountBucket.reset()
        notesBucket.reset()
        tagsBucket.reset()
        preferencesBucket.reset()

        accountBucket.stop()
        AppLog.add(Type.SYNC, "Stopped account bucket (AuthUtils)")
        notesBucket.stop()
        AppLog.add(Type.SYNC, "Stopped note bucket (AuthUtils)")
        tagsBucket.stop()
        AppLog.add(Type.SYNC, "Stopped tag bucket (AuthUtils)")
        preferencesBucket.stop()
        AppLog.add(Type.SYNC, "Stopped preference bucket (AuthUtils)")

        if (!WordPressTokenStore.from(context).clearTokenWithRetry()) {
            AppLog.add(Type.AUTH, "Unable to clear WordPress token during logout")
        }

        // Remove WordPress sites
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(PrefUtils.PREF_WORDPRESS_SITES)
            .apply()

        // Remove note scroll positions
        context.getSharedPreferences(SCROLL_POSITION_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()

        // Remove note last sync times
        context.getSharedPreferences(SYNC_TIME_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
    }

    override fun accountChanges(): Flow<AccountStatus> = callbackFlow {
        val listener = Bucket.OnNetworkChangeListener<Account> { bucket, type, key ->
            statusFor(bucket, type, key)?.let { trySend(it) }
        }
        accountBucket.addOnNetworkChangeListener(listener)
        awaitClose {
            accountBucket.removeOnNetworkChangeListener(listener)
        }
        // Only the latest verification state matters to the dialog surface, and
        // distinctUntilChanged reproduces the watcher's only-on-change notifications.
    }.buffer(Channel.CONFLATED).flowOn(ioDispatcher).distinctUntilChanged()

    override suspend fun verificationStatus(): AccountStatus = withContext(ioDispatcher) {
        val email = userEmail() ?: return@withContext AccountStatus.Unknown
        val account = try {
            accountBucket.get(Account.KEY_EMAIL_VERIFICATION)
        } catch (exception: BucketObjectMissingException) {
            return@withContext AccountStatus.Unknown
        }

        when {
            account.hasVerifiedEmail(email) -> AccountStatus.Verified
            else -> AccountStatus.Unverified(account.hasSentEmail(email))
        }
    }

    // The AccountVerificationWatcher.onNetworkChange decision table, returning null where the
    // watcher returned without updating state. Runs on the bucket's callback thread, as before.
    private fun statusFor(bucket: Bucket<Account>, type: Bucket.ChangeType, key: String?): AccountStatus? {
        // If the key for email verification is removed, the status is changed to UNVERIFIED immediately
        if (type == Bucket.ChangeType.REMOVE && Account.KEY_EMAIL_VERIFICATION == key) {
            return AccountStatus.Unverified(hasSentEmail = false)
        }

        val email = userEmail()
        if (!isValidChangeType(type, key) || email == null) {
            return null
        }

        // When a network change of type INDEX, INSERT or MODIFY is received, the account bucket
        // finished indexing or the account changed; either way re-read the verification state.
        val account = try {
            bucket.get(Account.KEY_EMAIL_VERIFICATION)
        } catch (exception: BucketObjectMissingException) {
            AppLog.add(Type.SYNC, "Account for email verification is missing")
            return null
        }

        return when {
            account.hasVerifiedEmail(email) -> AccountStatus.Verified
            else -> AccountStatus.Unverified(account.hasSentEmail(email))
        }
    }

    private fun isValidChangeType(type: Bucket.ChangeType, key: String?): Boolean =
        type == Bucket.ChangeType.INDEX ||
            (type == Bucket.ChangeType.INSERT && Account.KEY_EMAIL_VERIFICATION == key) ||
            (type == Bucket.ChangeType.MODIFY && Account.KEY_EMAIL_VERIFICATION == key)
}
