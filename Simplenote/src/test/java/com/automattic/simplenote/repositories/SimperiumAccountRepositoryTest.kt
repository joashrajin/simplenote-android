package com.automattic.simplenote.repositories

import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Account.KEY_EMAIL_VERIFICATION
import com.simperium.Simperium
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import com.simperium.client.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class SimperiumAccountRepositoryTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private lateinit var accountBucket: Bucket<Account>
    private lateinit var simperium: Simperium
    private lateinit var user: User
    private lateinit var account: Account
    private lateinit var repository: SimperiumAccountRepository

    @Before
    fun setUp() {
        accountBucket = mock()
        simperium = mock()
        user = mock()
        account = mock()
        whenever(simperium.user).thenReturn(user)
        whenever(user.email).thenReturn("person@example.com")
        whenever(accountBucket.get(KEY_EMAIL_VERIFICATION)).thenReturn(account)
        repository = SimperiumAccountRepository(
            accountBucket,
            simperium,
            coroutinesTestRule.testDispatcher,
        )
    }

    @Test
    fun verifiedStatusTakesPrecedenceOverSentEmail() = runTest {
        whenever(account.hasVerifiedEmail("person@example.com")).thenReturn(true)
        whenever(account.hasSentEmail("person@example.com")).thenReturn(true)

        assertEquals(
            AccountVerificationStatus.VERIFIED,
            repository.verificationStatus("person@example.com"),
        )
    }

    @Test
    fun sentEmailStatusIsReturnedForAnUnverifiedAccount() = runTest {
        whenever(account.hasSentEmail("person@example.com")).thenReturn(true)

        assertEquals(
            AccountVerificationStatus.SENT_EMAIL,
            repository.verificationStatus("person@example.com"),
        )
    }

    @Test
    fun unverifiedStatusIsReturnedWhenNoEmailWasSent() = runTest {
        assertEquals(
            AccountVerificationStatus.UNVERIFIED,
            repository.verificationStatus("person@example.com"),
        )
    }

    @Test
    fun missingVerificationObjectReturnsNoStatus() = runTest {
        whenever(accountBucket.get(KEY_EMAIL_VERIFICATION)).thenThrow(BucketObjectMissingException())

        assertNull(repository.verificationStatus("person@example.com"))
    }

    @Test
    fun oneShotReadPropagatesUnexpectedFailures() = runTest {
        val failure = IllegalStateException("read failed")
        whenever(accountBucket.get(KEY_EMAIL_VERIFICATION)).thenThrow(failure)

        val thrown = runCatching {
            repository.verificationStatus("person@example.com")
        }.exceptionOrNull()

        assertSame(failure, thrown)
    }

    @Test
    fun oneShotReadRunsOnTheInjectedIoDispatcher() = runTest {
        val ioDispatcher = QueueingDispatcher()
        val queuedRepository = repositoryWithDispatcher(ioDispatcher)
        val status = async { queuedRepository.verificationStatus("person@example.com") }

        runCurrent()
        verify(accountBucket, never()).get(any())

        ioDispatcher.runAll()
        verify(accountBucket).get(KEY_EMAIL_VERIFICATION)
        runCurrent()

        assertEquals(AccountVerificationStatus.UNVERIFIED, status.await())
    }

    @Test
    fun changeFlowIsColdAndHasNoEagerSnapshot() = runTest {
        repository.verificationStatusChanges()

        verify(accountBucket, never()).addOnNetworkChangeListener(any())
        verify(accountBucket, never()).get(any())
    }

    @Test
    fun changeFlowReadsTheAccountOnTheInjectedIoDispatcher() = runTest {
        val ioDispatcher = QueueingDispatcher()
        repository = repositoryWithDispatcher(ioDispatcher)
        val collected = collectChanges()

        val listener = networkListener()
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        verify(accountBucket, never()).get(any())

        ioDispatcher.runAll()
        verify(accountBucket).get(KEY_EMAIL_VERIFICATION)
        runCurrent()
        assertEquals(listOf(AccountVerificationStatus.UNVERIFIED), collected.statuses)

        collected.job.cancel()
        runCurrent()
        verify(accountBucket).removeOnNetworkChangeListener(listener)
    }

    @Test
    fun indexProducesTheFirstServerConfirmedStatus() = runTest {
        whenever(account.hasVerifiedEmail("person@example.com")).thenReturn(true)
        val collected = collectChanges()

        assertTrue(collected.statuses.isEmpty())
        verify(accountBucket, never()).get(any())

        networkListener().onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, "ignored")
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.VERIFIED), collected.statuses)
    }

    @Test
    fun keyedInsertAndModifyReevaluateWhileUnrelatedChangesAreIgnored() = runTest {
        whenever(account.hasSentEmail("person@example.com")).thenReturn(true, false)
        val collected = collectChanges()
        val listener = networkListener()

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INSERT, "other")
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.MODIFY, "other")
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.REMOVE, "other")
        runCurrent()

        assertTrue(collected.statuses.isEmpty())
        verify(accountBucket, never()).get(any())

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INSERT, KEY_EMAIL_VERIFICATION)
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.MODIFY, KEY_EMAIL_VERIFICATION)
        runCurrent()

        assertEquals(
            listOf(AccountVerificationStatus.SENT_EMAIL, AccountVerificationStatus.UNVERIFIED),
            collected.statuses,
        )
    }

    @Test
    fun keyedRemovalImmediatelyEmitsUnverifiedWithoutReadingUserOrBucket() = runTest {
        whenever(simperium.user).thenReturn(null)
        val collected = collectChanges()

        networkListener().onNetworkChange(
            accountBucket,
            Bucket.ChangeType.REMOVE,
            KEY_EMAIL_VERIFICATION,
        )
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.UNVERIFIED), collected.statuses)
        verify(simperium, never()).user
        verify(accountBucket, never()).get(any())
    }

    @Test
    fun refreshWithNoCurrentEmailEmitsNothing() = runTest {
        whenever(user.email).thenReturn(null)
        val collected = collectChanges()

        networkListener().onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()

        assertTrue(collected.statuses.isEmpty())
        verify(accountBucket, never()).get(any())
    }

    @Test
    fun missingVerificationObjectDuringRefreshEmitsNothing() = runTest {
        whenever(accountBucket.get(KEY_EMAIL_VERIFICATION)).thenThrow(BucketObjectMissingException())
        val collected = collectChanges()

        networkListener().onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()

        assertTrue(collected.statuses.isEmpty())
    }

    @Test
    fun unexpectedRefreshFailureDoesNotStopLaterChanges() = runTest {
        val failure = IllegalStateException("read failed")
        whenever(accountBucket.get(KEY_EMAIL_VERIFICATION)).thenThrow(failure).thenReturn(account)
        val collected = collectChanges()
        val listener = networkListener()

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        assertTrue(collected.statuses.isEmpty())
        assertTrue(collected.job.isActive)

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        assertEquals(listOf(AccountVerificationStatus.UNVERIFIED), collected.statuses)
    }

    @Test
    fun repeatedStatusesAreDeduplicatedWhileTransitionsStayOrdered() = runTest {
        whenever(account.hasVerifiedEmail("person@example.com")).thenReturn(false, false, false, true)
        whenever(account.hasSentEmail("person@example.com")).thenReturn(false, true, true)
        val collected = collectChanges()
        val listener = networkListener()

        repeat(4) {
            listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
            runCurrent()
        }

        assertEquals(
            listOf(
                AccountVerificationStatus.UNVERIFIED,
                AccountVerificationStatus.SENT_EMAIL,
                AccountVerificationStatus.VERIFIED,
            ),
            collected.statuses,
        )
    }

    @Test
    fun supersededIntermediateObjectStateIsNotEmitted() = runTest {
        val ioDispatcher = QueueingDispatcher()
        repository = repositoryWithDispatcher(ioDispatcher)
        var isVerified = false
        whenever(account.hasVerifiedEmail("person@example.com")).thenAnswer { isVerified }
        val collected = collectChanges()
        val listener = networkListener()

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        isVerified = true
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.MODIFY, KEY_EMAIL_VERIFICATION)

        ioDispatcher.runAll()
        runCurrent()
        ioDispatcher.runAll()
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.VERIFIED), collected.statuses)

        collected.job.cancel()
        runCurrent()
    }

    @Test
    fun queuedChangeForThePreviousUserIsDiscarded() = runTest {
        val ioDispatcher = QueueingDispatcher()
        repository = repositoryWithDispatcher(ioDispatcher)
        var currentEmail = "first@example.com"
        whenever(user.email).thenAnswer { currentEmail }
        whenever(account.hasVerifiedEmail("second@example.com")).thenReturn(true)
        val collected = collectChanges()
        val listener = networkListener()

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        currentEmail = "second@example.com"
        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        ioDispatcher.runAll()
        runCurrent()
        ioDispatcher.runAll()
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.VERIFIED), collected.statuses)
        verify(accountBucket).get(KEY_EMAIL_VERIFICATION)
        verify(account, never()).hasVerifiedEmail("first@example.com")
        verify(account, never()).hasSentEmail("first@example.com")

        collected.job.cancel()
        runCurrent()
    }

    @Test
    fun cancellationRemovesTheRegisteredListenerAndStopsDelivery() = runTest {
        val collected = collectChanges()
        val listener = networkListener()

        collected.job.cancel()
        runCurrent()
        verify(accountBucket).removeOnNetworkChangeListener(listener)

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        assertTrue(collected.statuses.isEmpty())
    }

    @Test
    fun queuedChangeIsNotReadAfterCollectionCancellation() = runTest {
        val ioDispatcher = QueueingDispatcher()
        repository = repositoryWithDispatcher(ioDispatcher)
        val collected = collectChanges()
        val listener = networkListener()

        listener.onNetworkChange(accountBucket, Bucket.ChangeType.INDEX, null)
        runCurrent()
        verify(accountBucket, never()).get(any())

        collected.job.cancel()
        runCurrent()

        verify(accountBucket).removeOnNetworkChangeListener(listener)
        ioDispatcher.runAll()
        verify(accountBucket, never()).get(any())
        assertTrue(collected.statuses.isEmpty())
    }

    private fun TestScope.collectChanges(): CollectedChanges {
        val statuses = mutableListOf<AccountVerificationStatus>()
        val job = backgroundScope.launch {
            repository.verificationStatusChanges().collect(statuses::add)
        }
        runCurrent()
        return CollectedChanges(job, statuses)
    }

    private fun networkListener(): Bucket.OnNetworkChangeListener<Account> =
        argumentCaptor<Bucket.OnNetworkChangeListener<Account>>().run {
            verify(accountBucket).addOnNetworkChangeListener(capture())
            firstValue
        }

    private fun repositoryWithDispatcher(dispatcher: CoroutineDispatcher) =
        SimperiumAccountRepository(accountBucket, simperium, dispatcher)

    private data class CollectedChanges(
        val job: Job,
        val statuses: List<AccountVerificationStatus>,
    )

    private class QueueingDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }
}
