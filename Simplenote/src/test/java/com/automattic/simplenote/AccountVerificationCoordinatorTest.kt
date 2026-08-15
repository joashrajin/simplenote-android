package com.automattic.simplenote

import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.AccountVerificationStatus
import com.automattic.simplenote.repositories.AccountVerificationUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AccountVerificationCoordinatorTest {
    @get:Rule
    val coroutineTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    @Test
    fun aMatchingUpdateRendersImmediately() = runTest {
        val repository = FakeAccountRepository()
        val rendered = mutableListOf<AccountVerificationStatus>()
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            { presentation -> rendered.add(presentation.update.status) },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.UNVERIFIED), rendered)
    }

    @Test
    fun anUnavailableHostRetainsOnlyTheLatestUpdate() = runTest {
        val repository = FakeAccountRepository()
        val rendered = mutableListOf<AccountVerificationStatus>()
        var hostAvailable = false
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            { presentation ->
                if (hostAvailable) {
                    rendered.add(presentation.update.status)
                }
                hostAvailable
            },
            { hostAvailable },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        repository.emit(update(AccountVerificationStatus.SENT_EMAIL))
        runCurrent()
        assertEquals(emptyList<AccountVerificationStatus>(), rendered)

        hostAvailable = true
        coordinator.onHostAvailable()

        assertEquals(listOf(AccountVerificationStatus.SENT_EMAIL), rendered)
    }

    @Test
    fun anUpdateForAnotherAccountIsNotRetained() = runTest {
        val repository = FakeAccountRepository()
        val rendered = mutableListOf<AccountVerificationStatus>()
        var activeEmail = "second@example.com"
        val coordinator = AccountVerificationCoordinator(
            repository,
            { activeEmail },
            { presentation -> rendered.add(presentation.update.status) },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.VERIFIED))
        runCurrent()
        activeEmail = "person@example.com"
        coordinator.onHostAvailable()

        assertEquals(emptyList<AccountVerificationStatus>(), rendered)
    }

    @Test
    fun changingAuthenticationSessionClearsPendingStateAndRestartsCollection() = runTest {
        val repository = FakeAccountRepository()
        val rendered = mutableListOf<AccountVerificationStatus>()
        var hostAvailable = false
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            { presentation ->
                if (hostAvailable) {
                    rendered.add(presentation.update.status)
                }
                hostAvailable
            },
            { hostAvailable },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()

        coordinator.onAuthenticationSessionChanging()
        runCurrent()
        hostAvailable = true
        coordinator.onHostAvailable()
        assertEquals(emptyList<AccountVerificationStatus>(), rendered)
        assertEquals(2, repository.collectionCount)

        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.UNVERIFIED), rendered)
    }

    @Test
    fun signedOutStateDropsPendingUpdates() = runTest {
        val repository = FakeAccountRepository()
        val rendered = mutableListOf<AccountVerificationStatus>()
        var activeEmail: String? = "person@example.com"
        val coordinator = AccountVerificationCoordinator(
            repository,
            { activeEmail },
            { presentation -> rendered.add(presentation.update.status) },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        activeEmail = null
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()
        activeEmail = "person@example.com"
        coordinator.onHostAvailable()

        assertEquals(emptyList<AccountVerificationStatus>(), rendered)
    }

    @Test
    fun changingSessionDismissesOldUiBeforeRenderingTheNewAccount() = runTest {
        val repository = FakeAccountRepository()
        val effects = mutableListOf<String>()
        var hostAvailable = false
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            { presentation ->
                effects.add("render:${presentation.update.status}")
                true
            },
            {
                if (hostAvailable) {
                    effects.add("dismiss")
                }
                hostAvailable
            },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        coordinator.onAuthenticationSessionChanging()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.SENT_EMAIL))
        runCurrent()
        assertEquals(emptyList<String>(), effects)

        hostAvailable = true
        coordinator.onHostAvailable()

        assertEquals(listOf("dismiss", "render:SENT_EMAIL"), effects)
    }

    @Test
    fun aFailedFlowRestartsOnTheNextAvailableHost() = runTest {
        val repository = FakeAccountRepository().apply {
            failNextCollection = true
        }
        val rendered = mutableListOf<AccountVerificationStatus>()
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            { presentation -> rendered.add(presentation.update.status) },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        assertEquals(1, repository.collectionCount)

        coordinator.onHostAvailable()
        runCurrent()
        assertEquals(2, repository.collectionCount)
        assertEquals(1, repository.subscriptionCount(0))
        repository.emit(update(AccountVerificationStatus.VERIFIED))
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.VERIFIED), rendered)
    }

    @Test
    fun recoveryDoesNotRenderTheSameStatusTwiceWithinOneSession() = runTest {
        val repository = FakeAccountRepository().apply {
            failAfterNextEmission = true
        }
        val rendered = mutableListOf<AccountVerificationStatus>()
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            { presentation -> rendered.add(presentation.update.status) },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()
        assertEquals(listOf(AccountVerificationStatus.UNVERIFIED), rendered)

        coordinator.onHostAvailable()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()
        assertEquals(listOf(AccountVerificationStatus.UNVERIFIED), rendered)

        repository.emit(update(AccountVerificationStatus.SENT_EMAIL))
        runCurrent()
        assertEquals(
            listOf(AccountVerificationStatus.UNVERIFIED, AccountVerificationStatus.SENT_EMAIL),
            rendered,
        )
    }

    @Test
    fun aQueuedPresentationIsRejectedAfterTheSameAccountStartsANewSession() = runTest {
        val repository = FakeAccountRepository()
        var presentation: AccountVerificationCoordinator.Presentation? = null
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            {
                presentation = it
                true
            },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()

        assertTrue(
            coordinator.isCurrentPresentation(
                presentation?.update?.email,
                presentation?.sessionGeneration ?: -1,
                presentation?.revision ?: -1,
                presentation?.processNonce,
            ),
        )

        coordinator.onAuthenticationSessionChanging()
        runCurrent()

        assertFalse(
            coordinator.isCurrentPresentation(
                presentation?.update?.email,
                presentation?.sessionGeneration ?: -1,
                presentation?.revision ?: -1,
                presentation?.processNonce,
            ),
        )
    }

    @Test
    fun changingSessionCancelsTheOldFlowAndRejectsItsLateUpdates() = runTest {
        val repository = FakeAccountRepository()
        val rendered = mutableListOf<AccountVerificationStatus>()
        var activeEmail = "old@example.com"
        val coordinator = AccountVerificationCoordinator(
            repository,
            { activeEmail },
            { presentation -> rendered.add(presentation.update.status) },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        assertEquals(1, repository.subscriptionCount(0))

        coordinator.onAuthenticationSessionChanging()
        runCurrent()
        assertEquals(0, repository.subscriptionCount(0))
        assertEquals(1, repository.subscriptionCount(1))

        activeEmail = "person@example.com"
        repository.emitAt(0, update(AccountVerificationStatus.UNVERIFIED))
        repository.emitAt(1, update(AccountVerificationStatus.SENT_EMAIL))
        runCurrent()

        assertEquals(listOf(AccountVerificationStatus.SENT_EMAIL), rendered)
    }

    @Test
    fun aNewStatusInvalidatesEveryOlderPresentationWithinTheSameSession() = runTest {
        val repository = FakeAccountRepository()
        val presentations = mutableListOf<AccountVerificationCoordinator.Presentation>()
        val coordinator = AccountVerificationCoordinator(
            repository,
            { "person@example.com" },
            {
                presentations.add(it)
                true
            },
            { true },
            backgroundScope,
        )

        coordinator.start()
        runCurrent()
        repository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()
        val unverified = presentations.last()
        assertTrue(coordinator.isCurrentPresentation(unverified))

        repository.emit(update(AccountVerificationStatus.SENT_EMAIL))
        runCurrent()
        val sentEmail = presentations.last()
        assertFalse(coordinator.isCurrentPresentation(unverified))
        assertTrue(coordinator.isCurrentPresentation(sentEmail))

        repository.emit(update(AccountVerificationStatus.VERIFIED))
        runCurrent()
        val verified = presentations.last()
        assertFalse(coordinator.isCurrentPresentation(sentEmail))
        assertTrue(coordinator.isCurrentPresentation(verified))
    }

    @Test
    fun aRestoredPresentationFromAnEarlierProcessIsRejected() = runTest {
        val oldRepository = FakeAccountRepository()
        var oldPresentation: AccountVerificationCoordinator.Presentation? = null
        val oldCoordinator = AccountVerificationCoordinator(
            oldRepository,
            { "person@example.com" },
            {
                oldPresentation = it
                true
            },
            { true },
            backgroundScope,
            "old-process",
        )
        oldCoordinator.start()
        runCurrent()
        oldRepository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()

        val newRepository = FakeAccountRepository()
        var newPresentation: AccountVerificationCoordinator.Presentation? = null
        val newCoordinator = AccountVerificationCoordinator(
            newRepository,
            { "person@example.com" },
            {
                newPresentation = it
                true
            },
            { true },
            backgroundScope,
            "new-process",
        )
        newCoordinator.start()
        runCurrent()
        newRepository.emit(update(AccountVerificationStatus.UNVERIFIED))
        runCurrent()

        assertEquals(oldPresentation?.sessionGeneration, newPresentation?.sessionGeneration)
        assertEquals(oldPresentation?.revision, newPresentation?.revision)
        assertFalse(newCoordinator.isCurrentPresentation(requireNotNull(oldPresentation)))
        assertTrue(newCoordinator.isCurrentPresentation(requireNotNull(newPresentation)))
    }

    private class FakeAccountRepository : AccountRepository {
        private val flows = mutableListOf<MutableSharedFlow<AccountVerificationUpdate>>()
        var collectionCount = 0
            private set
        var failAfterNextEmission = false
        var failNextCollection = false

        override suspend fun verificationStatus(email: String): AccountVerificationStatus? = null

        override fun verificationStatusChanges(): Flow<AccountVerificationUpdate> {
            collectionCount++
            if (failNextCollection) {
                failNextCollection = false
                return kotlinx.coroutines.flow.flow { throw IllegalStateException("collection failed") }
            }
            val updates = MutableSharedFlow<AccountVerificationUpdate>(extraBufferCapacity = 8)
                .also(flows::add)
            return kotlinx.coroutines.flow.flow {
                updates.collect { update ->
                    emit(update)
                    if (failAfterNextEmission) {
                        failAfterNextEmission = false
                        throw IllegalStateException("collection failed after emission")
                    }
                }
            }
        }

        suspend fun emit(update: AccountVerificationUpdate) {
            flows.last().emit(update)
        }

        suspend fun emitAt(index: Int, update: AccountVerificationUpdate) {
            flows[index].emit(update)
        }

        fun subscriptionCount(index: Int) = flows[index].subscriptionCount.value
    }

    private fun update(status: AccountVerificationStatus) =
        AccountVerificationUpdate("person@example.com", status)

    private fun AccountVerificationCoordinator.isCurrentPresentation(
        presentation: AccountVerificationCoordinator.Presentation,
    ) =
        isCurrentPresentation(
            presentation.update.email,
            presentation.sessionGeneration,
            presentation.revision,
            presentation.processNonce,
        )
}
