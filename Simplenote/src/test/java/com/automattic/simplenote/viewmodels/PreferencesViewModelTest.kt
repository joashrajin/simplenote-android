package com.automattic.simplenote.viewmodels

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

@ExperimentalCoroutinesApi
class PreferencesViewModelTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val notesRepository = mock<NotesRepository>()

    private fun advanceUntilIdle() = coroutinesTestRule.testDispatcher.scheduler.advanceUntilIdle()

    private fun observe(
        viewModel: PreferencesViewModel,
        initialState: Lifecycle.State = Lifecycle.State.STARTED,
    ): ObservedDecisions {
        val lifecycleDispatcher = UnconfinedTestDispatcher(coroutinesTestRule.testDispatcher.scheduler)
        val owner = TestLifecycleOwner(initialState, lifecycleDispatcher)
        val decisions = mutableListOf<LogoutDecision>()
        observeLogoutDecisions(viewModel.logoutDecisions, owner) { decision -> decisions.add(decision) }
        advanceUntilIdle()
        return ObservedDecisions(owner, decisions)
    }

    @Test
    fun unsyncedNotesWarnWithoutProceeding() {
        notesRepository.stub { onBlocking { hasUnsyncedNotes() }.doReturn(true) }
        val viewModel = PreferencesViewModel(notesRepository)
        val observed = observe(viewModel)

        viewModel.checkLogoutSafety()
        advanceUntilIdle()

        assertEquals(listOf(LogoutDecision.WARN_UNSYNCED), observed.decisions)
    }

    @Test
    fun fullySyncedNotesProceedWithoutWarning() {
        notesRepository.stub { onBlocking { hasUnsyncedNotes() }.doReturn(false) }
        val viewModel = PreferencesViewModel(notesRepository)
        val observed = observe(viewModel)

        viewModel.checkLogoutSafety()
        advanceUntilIdle()

        assertEquals(listOf(LogoutDecision.PROCEED), observed.decisions)
    }

    @Test
    fun repositoryFailureWarnsInsteadOfProceeding() {
        notesRepository.stub {
            onBlocking { hasUnsyncedNotes() }.doThrow(IllegalStateException("query failed"))
        }
        val viewModel = PreferencesViewModel(notesRepository)
        val observed = observe(viewModel)

        viewModel.checkLogoutSafety()
        advanceUntilIdle()

        assertEquals(listOf(LogoutDecision.WARN_UNSYNCED), observed.decisions)
    }

    @Test
    fun repeatedTapsWhileCheckingRunOneRepositoryQuery() {
        val gate = CompletableDeferred<Boolean>()
        var calls = 0
        notesRepository.stub {
            onBlocking { hasUnsyncedNotes() }.doSuspendableAnswer {
                calls++
                gate.await()
            }
        }
        val viewModel = PreferencesViewModel(notesRepository)
        val observed = observe(viewModel)

        viewModel.checkLogoutSafety()
        viewModel.checkLogoutSafety()
        advanceUntilIdle()
        gate.complete(false)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(listOf(LogoutDecision.PROCEED), observed.decisions)
    }

    @Test
    fun aLaterTapCanRunANewCompletedCheck() {
        var calls = 0
        notesRepository.stub {
            onBlocking { hasUnsyncedNotes() }.doSuspendableAnswer {
                calls++
                calls == 2
            }
        }
        val viewModel = PreferencesViewModel(notesRepository)
        val observed = observe(viewModel)

        viewModel.checkLogoutSafety()
        advanceUntilIdle()
        viewModel.checkLogoutSafety()
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(
            listOf(LogoutDecision.PROCEED, LogoutDecision.WARN_UNSYNCED),
            observed.decisions,
        )
    }

    @Test
    fun clearingTheViewModelPreventsDeliveryWhenTheRepositorySwallowsCancellation() {
        val gate = CompletableDeferred<Unit>()
        notesRepository.stub {
            onBlocking { hasUnsyncedNotes() }.doSuspendableAnswer {
                try {
                    gate.await()
                    false
                } catch (exception: CancellationException) {
                    true
                }
            }
        }
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PreferencesViewModel(notesRepository) as T
        }
        val viewModel = ViewModelProvider(owner, factory)[PreferencesViewModel::class.java]
        val observed = observe(viewModel)

        viewModel.checkLogoutSafety()
        advanceUntilIdle()
        store.clear()
        advanceUntilIdle()

        assertTrue(observed.decisions.isEmpty())
    }

    @Test
    fun aConsumedDecisionIsNotReplayedToAReplacementObserver() {
        notesRepository.stub { onBlocking { hasUnsyncedNotes() }.doReturn(false) }
        val viewModel = PreferencesViewModel(notesRepository)
        val first = observe(viewModel)

        viewModel.checkLogoutSafety()
        advanceUntilIdle()
        first.owner.currentState = Lifecycle.State.DESTROYED
        val replacement = observe(viewModel)

        assertEquals(listOf(LogoutDecision.PROCEED), first.decisions)
        assertTrue(replacement.decisions.isEmpty())
    }

    @Test
    fun aDecisionEmittedWhileInactiveIsDeliveredOnceToTheReplacementObserver() {
        notesRepository.stub { onBlocking { hasUnsyncedNotes() }.doReturn(true) }
        val viewModel = PreferencesViewModel(notesRepository)
        val inactive = observe(viewModel)

        inactive.owner.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()
        viewModel.checkLogoutSafety()
        advanceUntilIdle()
        inactive.owner.currentState = Lifecycle.State.DESTROYED
        val replacement = observe(viewModel)
        replacement.owner.currentState = Lifecycle.State.DESTROYED
        val secondReplacement = observe(viewModel)

        assertTrue(inactive.decisions.isEmpty())
        assertEquals(listOf(LogoutDecision.WARN_UNSYNCED), replacement.decisions)
        assertTrue(secondReplacement.decisions.isEmpty())
    }

    private data class ObservedDecisions(
        val owner: TestLifecycleOwner,
        val decisions: List<LogoutDecision>,
    )
}
