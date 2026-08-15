package com.automattic.simplenote

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.AccountVerificationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AccountVerificationResumeCheckTest {
    @get:Rule
    val coroutineTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    @Test
    fun checksExactlyOnceForEachResumedEntry() = runTest {
        val repository = FakeAccountRepository()
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        val check = AccountVerificationResumeCheck(repository, backgroundScope)

        check.start(owner.lifecycle, "person@example.com") {}
        runCurrent()
        assertEquals(emptyList<String>(), repository.requestedEmails)

        owner.currentState = Lifecycle.State.RESUMED
        runCurrent()
        runCurrent()
        assertEquals(listOf("person@example.com"), repository.requestedEmails)

        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        owner.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(
            listOf("person@example.com", "person@example.com"),
            repository.requestedEmails,
        )
    }

    @Test
    fun invokesTheCallbackOnlyForVerifiedStatus() = runTest {
        val repository = FakeAccountRepository().apply {
            responses.add(null)
            responses.add(AccountVerificationStatus.SENT_EMAIL)
            responses.add(AccountVerificationStatus.UNVERIFIED)
            responses.add(AccountVerificationStatus.VERIFIED)
        }
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        val check = AccountVerificationResumeCheck(repository, backgroundScope)
        var verifiedCallbacks = 0

        check.start(owner.lifecycle, "person@example.com") { verifiedCallbacks++ }
        repeat(4) {
            owner.currentState = Lifecycle.State.RESUMED
            runCurrent()
            owner.currentState = Lifecycle.State.STARTED
            runCurrent()
        }

        assertEquals(1, verifiedCallbacks)
        assertEquals(4, repository.requestedEmails.size)
    }

    @Test
    fun leavingResumedCancelsAStaleVerifiedResult() = runTest {
        val lookupStarted = CompletableDeferred<Unit>()
        val repository = FakeAccountRepository().apply {
            response = {
                lookupStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    AccountVerificationStatus.VERIFIED
                }
            }
        }
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        val check = AccountVerificationResumeCheck(repository, backgroundScope)
        var verifiedCallbacks = 0

        check.start(owner.lifecycle, "person@example.com") { verifiedCallbacks++ }
        owner.currentState = Lifecycle.State.RESUMED
        lookupStarted.await()
        owner.currentState = Lifecycle.State.STARTED
        runCurrent()

        assertEquals(0, verifiedCallbacks)
    }

    @Test
    fun aFailedReadDoesNotDismissAndTheNextResumeRetries() = runTest {
        val repository = FakeAccountRepository().apply {
            failures.add(IllegalStateException("lookup failed"))
            responses.add(AccountVerificationStatus.VERIFIED)
        }
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        val check = AccountVerificationResumeCheck(repository, backgroundScope)
        var verifiedCallbacks = 0

        check.start(owner.lifecycle, "person@example.com") { verifiedCallbacks++ }
        owner.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(0, verifiedCallbacks)

        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        owner.currentState = Lifecycle.State.RESUMED
        runCurrent()

        assertEquals(1, verifiedCallbacks)
        assertEquals(2, repository.requestedEmails.size)
    }

    @Test
    fun neverCollectsTheAccountChangeFlow() = runTest {
        val repository = FakeAccountRepository()
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, UnconfinedTestDispatcher(testScheduler))
        val check = AccountVerificationResumeCheck(repository, backgroundScope)

        check.start(owner.lifecycle, "person@example.com") {}
        runCurrent()

        assertEquals(0, repository.changeFlowRequests)
    }

    private class FakeAccountRepository : AccountRepository {
        val requestedEmails = mutableListOf<String>()
        val responses = ArrayDeque<AccountVerificationStatus?>()
        val failures = ArrayDeque<Exception>()
        var changeFlowRequests = 0
        var response: (suspend () -> AccountVerificationStatus?)? = null

        override suspend fun verificationStatus(email: String): AccountVerificationStatus? {
            requestedEmails.add(email)
            response?.let { return it() }
            if (failures.isNotEmpty()) {
                throw failures.removeFirst()
            }
            return if (responses.isEmpty()) null else responses.removeFirst()
        }

        override fun verificationStatusChanges(): Flow<AccountVerificationStatus> {
            changeFlowRequests++
            return emptyFlow()
        }
    }
}
