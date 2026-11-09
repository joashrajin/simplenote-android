package com.automattic.simplenote.repositories

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.utils.AppLog
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.WordPressTokenStore
import com.simperium.Simperium
import com.simperium.android.AndroidClient
import com.simperium.android.AsyncAuthClient
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import com.simperium.client.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class SimperiumAccountRepositoryTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private val simperium: Simperium = mock()
    private val accountBucket: Bucket<Account> = mock()
    private val notesBucket: Bucket<Note> = mock()
    private val tagsBucket: Bucket<Tag> = mock()
    private val preferencesBucket: Bucket<Preferences> = mock()
    private val context: Context = mock()
    private lateinit var repository: SimperiumAccountRepository

    @Before
    fun setUp() {
        repository = repository(coroutinesTestRule.testDispatcher)
    }

    private fun repository(mainDispatcher: CoroutineDispatcher) = SimperiumAccountRepository(
        simperium,
        accountBucket,
        notesBucket,
        tagsBucket,
        preferencesBucket,
        coroutinesTestRule.testDispatcher,
        mainDispatcher,
        context,
    )

    // region session

    @Test
    fun sessionWithTokenAndEmailIsAuthorized() {
        val user = User().apply {
            email = "test@test.com"
            accessToken = "124556"
        }
        whenever(simperium.user).thenReturn(user)

        assertEquals(UserSession.AuthorizedUser(user), repository.session())
    }

    @Test
    fun sessionWithoutTokenIsUnauthorized() {
        val user = User().apply {
            email = "test@test.com"
            accessToken = null
        }
        whenever(simperium.user).thenReturn(user)

        assertEquals(UserSession.UnauthorizedUser, repository.session())
    }

    @Test
    fun sessionWithoutEmailIsUnauthorized() {
        val user = User().apply {
            email = null
            accessToken = "124556"
        }
        whenever(simperium.user).thenReturn(user)

        assertEquals(UserSession.UnauthorizedUser, repository.session())
    }

    @Test
    fun sessionWithoutUserIsUnauthorized() {
        whenever(simperium.user).thenReturn(null)

        assertEquals(UserSession.UnauthorizedUser, repository.session())
    }

    // endregion

    // region isLoggedIn / userEmail

    @Test
    fun isLoggedInRequiresAnAuthorizedStatus() {
        val user = User().apply { status = User.Status.AUTHORIZED }
        whenever(simperium.user).thenReturn(user)

        assertTrue(repository.isLoggedIn())

        user.status = User.Status.NOT_AUTHORIZED
        assertFalse(repository.isLoggedIn())

        whenever(simperium.user).thenReturn(null)
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun userEmailReadsTheSimperiumUser() {
        whenever(simperium.user).thenReturn(User().apply { email = "test@test.com" })
        assertEquals("test@test.com", repository.userEmail())

        whenever(simperium.user).thenReturn(null)
        assertNull(repository.userEmail())
    }

    // endregion

    // region loginWithToken

    @Test
    fun loginWithTokenAuthorizesTheUserAndPersistsTheSession() = runTest {
        val user = User()
        whenever(simperium.user).thenReturn(user)
        val editor = editor()
        val preferences = mock<SharedPreferences>()
        whenever(preferences.edit()).thenReturn(editor)

        Mockito.mockStatic(AndroidClient::class.java).use { androidClient ->
            androidClient.`when`<SharedPreferences> { AndroidClient.sharedPreferences(context) }
                .thenReturn(preferences)

            repository.loginWithToken("test@test.com", "token123")
        }

        assertEquals(User.Status.AUTHORIZED, user.status)
        assertEquals("test@test.com", user.email)
        assertEquals("token123", user.accessToken)
        inOrder(editor) {
            verify(editor).putString(AsyncAuthClient.USER_ACCESS_TOKEN_PREFERENCE, "token123")
            verify(editor).putString(AsyncAuthClient.USER_EMAIL_PREFERENCE, "test@test.com")
            verify(editor).apply()
        }
    }

    // endregion

    // region logout

    @Test
    fun logoutReproducesTheLegacySequenceInOrder() = runTest {
        val tokenStore = tokenStore(cleared = true)
        val defaultEditor = editor()
        val scrollEditor = editor()
        val syncEditor = editor()
        val defaultPreferences = preferencesWith(defaultEditor)
        stubNamedPreferences(scrollEditor, syncEditor)

        mockLogoutStatics(tokenStore, defaultPreferences).use {
            repository.logout()
        }

        inOrder(simperium, accountBucket, notesBucket, tagsBucket, preferencesBucket) {
            verify(simperium).deauthorizeUser()
            verify(accountBucket).reset()
            verify(notesBucket).reset()
            verify(tagsBucket).reset()
            verify(preferencesBucket).reset()
            verify(accountBucket).stop()
            verify(notesBucket).stop()
            verify(tagsBucket).stop()
            verify(preferencesBucket).stop()
        }
        verify(tokenStore).clearTokenWithRetry()
        inOrder(defaultEditor) {
            verify(defaultEditor).remove(PrefUtils.PREF_WORDPRESS_SITES)
            verify(defaultEditor).apply()
        }
        inOrder(scrollEditor) {
            verify(scrollEditor).clear()
            verify(scrollEditor).apply()
        }
        inOrder(syncEditor) {
            verify(syncEditor).clear()
            verify(syncEditor).apply()
        }
    }

    @Test
    fun logoutRunsTheFullSequenceAgainOnASecondCall() = runTest {
        val tokenStore = tokenStore(cleared = true)
        stubNamedPreferences(editor(), editor())
        val defaultPreferences = preferencesWith(editor())

        mockLogoutStatics(tokenStore, defaultPreferences).use {
            repository.logout()
            repository.logout()
        }

        // No idempotence guard on the repository: reset/stop tolerate re-runs, so a second
        // logout replays the whole legacy sequence rather than silently short-circuiting.
        verify(simperium, times(2)).deauthorizeUser()
        verify(accountBucket, times(2)).reset()
        verify(notesBucket, times(2)).reset()
        verify(tagsBucket, times(2)).reset()
        verify(preferencesBucket, times(2)).reset()
        verify(accountBucket, times(2)).stop()
        verify(notesBucket, times(2)).stop()
        verify(tagsBucket, times(2)).stop()
        verify(preferencesBucket, times(2)).stop()
        verify(tokenStore, times(2)).clearTokenWithRetry()
    }

    @Test
    fun logoutLogsWhenTheWordPressTokenClearFails() = runTest {
        val tokenStore = tokenStore(cleared = false)
        stubNamedPreferences(editor(), editor())
        val defaultPreferences = preferencesWith(editor())

        mockLogoutStatics(tokenStore, defaultPreferences).use {
            repository.logout()
        }

        verify(tokenStore).clearTokenWithRetry()
        assertTrue(AppLog.get().contains("Unable to clear WordPress token during logout"))
    }

    @Test
    fun logoutRunsItsSequenceOnTheInjectedMainDispatcher() = runTest {
        val tokenStore = tokenStore(cleared = true)
        stubNamedPreferences(editor(), editor())
        val defaultPreferences = preferencesWith(editor())
        val repository = repository(StandardTestDispatcher(coroutinesTestRule.testDispatcher.scheduler))

        mockLogoutStatics(tokenStore, defaultPreferences).use {
            val job = launch(coroutinesTestRule.testDispatcher) { repository.logout() }

            // The unconfined entry has run as far as it can inline: the whole sequence is
            // parked on the injected main dispatcher's queue, nothing has executed yet.
            verify(simperium, never()).deauthorizeUser()
            verify(accountBucket, never()).reset()

            advanceUntilIdle()

            verify(simperium).deauthorizeUser()
            verify(preferencesBucket).stop()
            assertTrue(job.isCompleted)
        }
    }

    // endregion

    // region accountChanges

    @Test
    fun accountChangesMapsTheWatcherDecisionTable() = runTest {
        sessionEmail("test@test.com")
        val account = account(verified = false, sentEmail = false)
        whenever(accountBucket.get(Account.KEY_EMAIL_VERIFICATION)).thenReturn(account)
        val (statuses, job) = collectStatuses()
        val listener = accountChangeListener()

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        whenever(account.hasSentEmail("test@test.com")).thenReturn(true)
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.MODIFY, Account.KEY_EMAIL_VERIFICATION)
        runCurrent()
        whenever(account.hasVerifiedEmail("test@test.com")).thenReturn(true)
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INSERT, Account.KEY_EMAIL_VERIFICATION)
        runCurrent()
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.REMOVE, Account.KEY_EMAIL_VERIFICATION)
        runCurrent()

        assertEquals(
            listOf(
                AccountStatus.Unverified(hasSentEmail = false),
                AccountStatus.Unverified(hasSentEmail = true),
                AccountStatus.Verified,
                AccountStatus.Unverified(hasSentEmail = false),
            ),
            statuses,
        )
        job.cancel()
    }

    @Test
    fun accountChangesSkipsIndeterminateEvents() = runTest {
        sessionEmail(null)
        val (statuses, job) = collectStatuses()
        val listener = accountChangeListener()

        // No session email.
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()

        // Invalid change type for the verification key.
        sessionEmail("test@test.com")
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.MODIFY, "some-other-key")
        runCurrent()

        // Account object missing from the bucket.
        whenever(accountBucket.get(Account.KEY_EMAIL_VERIFICATION))
            .thenThrow(BucketObjectMissingException())
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()

        assertEquals(emptyList<AccountStatus>(), statuses)
        job.cancel()
    }

    @Test
    fun accountChangesDropsConsecutiveDuplicates() = runTest {
        sessionEmail("test@test.com")
        val account = account(verified = true, sentEmail = false)
        whenever(accountBucket.get(Account.KEY_EMAIL_VERIFICATION)).thenReturn(account)
        val (statuses, job) = collectStatuses()
        val listener = accountChangeListener()

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.MODIFY, Account.KEY_EMAIL_VERIFICATION)
        runCurrent()

        assertEquals(listOf<AccountStatus>(AccountStatus.Verified), statuses)
        job.cancel()
    }

    @Test
    fun accountChangesCancellationRemovesTheListener() = runTest {
        val job = launch(coroutinesTestRule.testDispatcher) { repository.accountChanges().collect {} }
        runCurrent()
        val listener = accountChangeListener()

        job.cancel()
        advanceUntilIdle()

        verify(accountBucket).removeOnNetworkChangeListener(listener)
    }

    // endregion

    // region verificationStatus

    @Test
    fun verificationStatusOfVerifiedEmailIsVerified() = runTest {
        sessionEmail("test@test.com")
        val account = account(verified = true, sentEmail = false)
        whenever(accountBucket.get(Account.KEY_EMAIL_VERIFICATION)).thenReturn(account)

        assertEquals(AccountStatus.Verified, repository.verificationStatus())
    }

    @Test
    fun verificationStatusCarriesTheSentEmailSplit() = runTest {
        sessionEmail("test@test.com")
        val sentEmailAccount = account(verified = false, sentEmail = true)
        whenever(accountBucket.get(Account.KEY_EMAIL_VERIFICATION)).thenReturn(sentEmailAccount)

        assertEquals(AccountStatus.Unverified(hasSentEmail = true), repository.verificationStatus())

        val unverifiedAccount = account(verified = false, sentEmail = false)
        whenever(accountBucket.get(Account.KEY_EMAIL_VERIFICATION)).thenReturn(unverifiedAccount)

        assertEquals(AccountStatus.Unverified(hasSentEmail = false), repository.verificationStatus())
    }

    @Test
    fun verificationStatusWithoutAccountOrEmailIsUnknown() = runTest {
        sessionEmail("test@test.com")
        whenever(accountBucket.get(Account.KEY_EMAIL_VERIFICATION))
            .thenThrow(BucketObjectMissingException())

        assertEquals(AccountStatus.Unknown, repository.verificationStatus())

        sessionEmail(null)
        assertEquals(AccountStatus.Unknown, repository.verificationStatus())
    }

    // endregion

    private fun sessionEmail(email: String?) {
        whenever(simperium.user).thenReturn(if (email == null) null else User().apply { this.email = email })
    }

    private fun account(verified: Boolean, sentEmail: Boolean): Account {
        val account = mock<Account>()
        whenever(account.hasVerifiedEmail(any())).thenReturn(verified)
        whenever(account.hasSentEmail(any())).thenReturn(sentEmail)
        return account
    }

    private fun editor(): SharedPreferences.Editor {
        val editor = mock<SharedPreferences.Editor>()
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
        return editor
    }

    private fun preferencesWith(editor: SharedPreferences.Editor): SharedPreferences {
        val preferences = mock<SharedPreferences>()
        whenever(preferences.edit()).thenReturn(editor)
        return preferences
    }

    private fun tokenStore(cleared: Boolean): WordPressTokenStore {
        val tokenStore = mock<WordPressTokenStore>()
        whenever(tokenStore.clearTokenWithRetry()).thenReturn(cleared)
        return tokenStore
    }

    private fun stubNamedPreferences(scrollEditor: SharedPreferences.Editor, syncEditor: SharedPreferences.Editor) {
        val scrollPreferences = preferencesWith(scrollEditor)
        val syncPreferences = preferencesWith(syncEditor)
        whenever(context.getSharedPreferences(Simplenote.SCROLL_POSITION_PREFERENCES, Context.MODE_PRIVATE))
            .thenReturn(scrollPreferences)
        whenever(context.getSharedPreferences(Simplenote.SYNC_TIME_PREFERENCES, Context.MODE_PRIVATE))
            .thenReturn(syncPreferences)
    }

    private fun mockLogoutStatics(
        tokenStore: WordPressTokenStore,
        defaultPreferences: SharedPreferences,
    ): AutoCloseable {
        val tokenStoreStatic: MockedStatic<WordPressTokenStore> = Mockito.mockStatic(WordPressTokenStore::class.java)
        tokenStoreStatic.`when`<WordPressTokenStore> { WordPressTokenStore.from(context) }.thenReturn(tokenStore)
        val preferenceManagerStatic: MockedStatic<PreferenceManager> = Mockito.mockStatic(PreferenceManager::class.java)
        preferenceManagerStatic.`when`<SharedPreferences> { PreferenceManager.getDefaultSharedPreferences(context) }
            .thenReturn(defaultPreferences)
        return AutoCloseable {
            tokenStoreStatic.close()
            preferenceManagerStatic.close()
        }
    }

    private fun TestScope.collectStatuses(): Pair<List<AccountStatus>, Job> {
        val statuses = mutableListOf<AccountStatus>()
        val job = launch(coroutinesTestRule.testDispatcher) { repository.accountChanges().collect { statuses.add(it) } }
        runCurrent()
        return statuses to job
    }

    private fun accountChangeListener(): Bucket.OnNetworkChangeListener<Account> =
        argumentCaptor<Bucket.OnNetworkChangeListener<Account>>().run {
            verify(accountBucket).addOnNetworkChangeListener(capture())
            firstValue
        }
}
