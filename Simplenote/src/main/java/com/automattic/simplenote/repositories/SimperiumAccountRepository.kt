package com.automattic.simplenote.repositories

import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Account.KEY_EMAIL_VERIFICATION
import com.automattic.simplenote.utils.AppLog
import com.simperium.Simperium
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SimperiumAccountRepository @Inject constructor(
    private val accountBucket: Bucket<Account>,
    private val simperium: Simperium,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccountRepository {
    override suspend fun verificationStatus(email: String): AccountVerificationStatus? =
        withContext(ioDispatcher) { readVerificationStatus(email) }

    override fun verificationStatusChanges(): Flow<AccountVerificationUpdate> = callbackFlow {
        val listener = Bucket.OnNetworkChangeListener<Account> { _, type, key ->
            trySend(AccountChange(type, key, simperium.user?.email))
        }

        accountBucket.addOnNetworkChangeListener(listener)
        awaitClose { accountBucket.removeOnNetworkChangeListener(listener) }
    }
        .buffer(Channel.UNLIMITED)
        .mapNotNull(::verificationUpdateForChange)
        .distinctUntilChanged { previous, current ->
            previous.status == current.status && previous.email.equals(current.email, ignoreCase = true)
        }

    private suspend fun verificationUpdateForChange(change: AccountChange): AccountVerificationUpdate? {
        val isVerificationRemoval = change.type == Bucket.ChangeType.REMOVE &&
                change.key == KEY_EMAIL_VERIFICATION
        val shouldRefresh = change.type == Bucket.ChangeType.INDEX ||
                ((change.type == Bucket.ChangeType.INSERT || change.type == Bucket.ChangeType.MODIFY) &&
                        change.key == KEY_EMAIL_VERIFICATION)
        if (!isVerificationRemoval && !shouldRefresh) {
            return null
        }

        val email = change.email ?: return null
        return try {
            val update = withContext(ioDispatcher) {
                val currentEmail = simperium.user?.email
                if (email.equals(currentEmail, ignoreCase = true)) {
                    val status = if (isVerificationRemoval) {
                        AccountVerificationStatus.UNVERIFIED
                    } else {
                        readVerificationStatus(email)
                    }
                    val activeEmail = simperium.user?.email
                    if (email.equals(activeEmail, ignoreCase = true)) {
                        status?.let { AccountVerificationUpdate(email, it) }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            val currentEmail = simperium.user?.email
            update?.takeIf { it.email.equals(currentEmail, ignoreCase = true) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            AppLog.add(
                AppLog.Type.SYNC,
                "Unable to read account verification status (${exception.javaClass.simpleName})",
            )
            null
        }
    }

    private fun readVerificationStatus(email: String): AccountVerificationStatus? {
        val account = try {
            accountBucket.get(KEY_EMAIL_VERIFICATION)
        } catch (_: BucketObjectMissingException) {
            AppLog.add(AppLog.Type.SYNC, "Account for email verification is missing")
            return null
        }

        return when {
            account.hasVerifiedEmail(email) -> AccountVerificationStatus.VERIFIED
            account.hasSentEmail(email) -> AccountVerificationStatus.SENT_EMAIL
            else -> AccountVerificationStatus.UNVERIFIED
        }
    }

    private data class AccountChange(
        val type: Bucket.ChangeType,
        val key: String?,
        val email: String?,
    )
}
