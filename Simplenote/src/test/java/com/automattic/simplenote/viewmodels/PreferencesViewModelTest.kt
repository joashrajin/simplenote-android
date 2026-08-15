package com.automattic.simplenote.viewmodels

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.usecases.ExportNotesUseCase
import com.automattic.simplenote.utils.ExportNotesGate
import com.automattic.simplenote.utils.ExportSessionChangedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

@ExperimentalCoroutinesApi
class PreferencesViewModelTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val notesRepository = mock<NotesRepository>()
    private val exportNotesUseCase = mock<ExportNotesUseCase>()
    private val uri = mock<Uri>()
    private val exportNotesGate = ExportNotesGate()

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
        val viewModel = viewModel()
        val observed = observe(viewModel)

        viewModel.checkLogoutSafety()
        advanceUntilIdle()

        assertEquals(listOf(LogoutDecision.WARN_UNSYNCED), observed.decisions)
    }

    @Test
    fun fullySyncedNotesProceedWithoutWarning() {
        notesRepository.stub { onBlocking { hasUnsyncedNotes() }.doReturn(false) }
        val viewModel = viewModel()
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
        val viewModel = viewModel()
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
        val viewModel = viewModel()
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
        val viewModel = viewModel()
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
                PreferencesViewModel(
                    notesRepository,
                    exportNotesUseCase,
                    exportNotesGate,
                    coroutinesTestRule.testDispatcher,
                ) as T
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
        val viewModel = viewModel()
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
        val viewModel = viewModel()
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

    @Test
    fun aSuccessfulExportEmitsSuccess() {
        exportNotesUseCase.stub { onBlocking { invoke(uri, false, 0L) }.doReturn(Unit) }
        val viewModel = viewModel()
        val observed = observeExports(viewModel)

        viewModel.exportNotes(uri, unsyncedOnly = false)
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.SUCCESS), observed.results)
    }

    @Test
    fun anExportFailureEmitsFailure() {
        exportNotesUseCase.stub {
            onBlocking { invoke(uri, true, 0L) }.doSuspendableAnswer { throw IOException("write failed") }
        }
        val viewModel = viewModel()
        val observed = observeExports(viewModel)

        viewModel.exportNotes(uri, unsyncedOnly = true)
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.FAILURE), observed.results)
    }

    @Test
    fun exportCancellationEmitsNoResult() {
        exportNotesUseCase.stub {
            onBlocking { invoke(uri, false, 0L) }.doSuspendableAnswer { throw CancellationException() }
        }
        val viewModel = viewModel()
        val observed = observeExports(viewModel)

        viewModel.exportNotes(uri, unsyncedOnly = false)
        advanceUntilIdle()

        assertTrue(observed.results.isEmpty())
    }

    @Test
    fun anExportResultBufferedWhileInactiveIsDeliveredOnce() {
        exportNotesUseCase.stub { onBlocking { invoke(uri, false, 0L) }.doReturn(Unit) }
        val viewModel = viewModel()
        val inactive = observeExports(viewModel)
        inactive.owner.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()

        viewModel.exportNotes(uri, unsyncedOnly = false)
        advanceUntilIdle()
        inactive.owner.currentState = Lifecycle.State.DESTROYED
        val replacement = observeExports(viewModel)
        replacement.owner.currentState = Lifecycle.State.DESTROYED
        val secondReplacement = observeExports(viewModel)

        assertTrue(inactive.results.isEmpty())
        assertEquals(listOf(ExportResult.SUCCESS), replacement.results)
        assertTrue(secondReplacement.results.isEmpty())
    }

    @Test
    fun acceptedExportFinishesAfterViewModelClearBeforeTheNextViewModelExportRuns() {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        exportNotesUseCase.stub {
            onBlocking { invoke(uri, false, 0L) }.doSuspendableAnswer {
                calls++
                if (calls == 1) {
                    started.complete(Unit)
                    finish.await()
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
                PreferencesViewModel(
                    notesRepository,
                    exportNotesUseCase,
                    exportNotesGate,
                    coroutinesTestRule.testDispatcher,
                ) as T
        }
        val firstViewModel = ViewModelProvider(owner, factory)[PreferencesViewModel::class.java]
        val firstObserved = observeExports(firstViewModel)

        firstViewModel.exportNotes(uri, unsyncedOnly = false)
        advanceUntilIdle()
        assertTrue(started.isCompleted)
        store.clear()
        val secondViewModel = viewModel()
        val secondObserved = observeExports(secondViewModel)
        secondViewModel.exportNotes(uri, unsyncedOnly = false)
        advanceUntilIdle()
        assertEquals(1, calls)
        finish.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, calls)
        assertTrue(firstObserved.results.isEmpty())
        assertEquals(listOf(ExportResult.SUCCESS), secondObserved.results)
    }

    @Test
    fun acceptedExportRequestsDoNotOverlapAndBothDeliverResults() {
        val firstGate = CompletableDeferred<Unit>()
        var calls = 0
        exportNotesUseCase.stub {
            onBlocking { invoke(uri, false, 0L) }.doSuspendableAnswer {
                calls++
                if (calls == 1) {
                    firstGate.await()
                    throw IOException("first failed")
                }
            }
        }
        val viewModel = viewModel()
        val observed = observeExports(viewModel)

        viewModel.exportNotes(uri, unsyncedOnly = false)
        viewModel.exportNotes(uri, unsyncedOnly = false)
        advanceUntilIdle()
        assertEquals(1, calls)
        firstGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(2, observed.results.size)
        assertTrue(observed.results.contains(ExportResult.FAILURE))
        assertTrue(observed.results.contains(ExportResult.SUCCESS))
    }

    @Test
    fun invalidatingTheSessionRejectsAQueuedOldGenerationExport() {
        val context = mock<Context>()
        val contentResolver = mock<ContentResolver>()
        val secondUri = mock<Uri>()
        whenever(context.contentResolver).thenReturn(contentResolver)
        val firstStarted = CompletableDeferred<Unit>()
        val firstFinish = CompletableDeferred<Unit>()
        var calls = 0
        notesRepository.stub {
            onBlocking { allNotesForExport() }.doSuspendableAnswer {
                calls++
                firstStarted.complete(Unit)
                firstFinish.await()
                emptyList()
            }
        }
        val realUseCase = ExportNotesUseCase(
            notesRepository,
            context,
            coroutinesTestRule.testDispatcher,
            exportNotesGate,
        )
        val viewModel = PreferencesViewModel(
            notesRepository,
            realUseCase,
            exportNotesGate,
            coroutinesTestRule.testDispatcher,
        )
        val observed = observeExports(viewModel)

        viewModel.exportNotes(uri, unsyncedOnly = false)
        viewModel.exportNotes(secondUri, unsyncedOnly = false)
        advanceUntilIdle()
        assertTrue(firstStarted.isCompleted)
        exportNotesGate.invalidateSession()

        firstFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(listOf(ExportResult.FAILURE, ExportResult.FAILURE), observed.results)
        verify(contentResolver, never()).openOutputStream(uri, "wt")
        verify(contentResolver, never()).openOutputStream(secondUri, "wt")
    }

    @Test
    fun cancellingAQueuedGateCallerDoesNotBlockLaterExports() = runTest {
        val gate = ExportNotesGate()
        val firstRelease = CompletableDeferred<Unit>()
        val firstJob = launch {
            gate.runExport { firstRelease.await() }
        }
        runCurrent()
        val secondJob = launch { gate.runExport {} }
        runCurrent()

        secondJob.cancelAndJoin()
        firstRelease.complete(Unit)
        firstJob.join()

        gate.runExport {}
    }

    @Test
    fun invalidatingTheSessionRejectsTheOldGeneration() = runTest {
        val gate = ExportNotesGate()
        val oldGeneration = gate.currentGeneration()
        gate.invalidateSession()

        val failure = runCatching {
            gate.ensureCurrent(oldGeneration)
        }.exceptionOrNull()

        assertTrue(failure is ExportSessionChangedException)
    }

    @Test
    fun sessionResetInvalidatesBeforeWorkAndSuppressesAReentrantReset() {
        val gate = ExportNotesGate()
        val oldGeneration = gate.currentGeneration()

        assertTrue(gate.beginSessionReset())
        assertTrue(runCatching { gate.ensureCurrent(oldGeneration) }.exceptionOrNull() is ExportSessionChangedException)
        assertTrue(!gate.beginSessionReset())

        gate.finishSessionReset()
        assertTrue(gate.beginSessionReset())
        gate.finishSessionReset()
    }

    private fun viewModel() = PreferencesViewModel(
        notesRepository,
        exportNotesUseCase,
        exportNotesGate,
        coroutinesTestRule.testDispatcher,
    )

    private fun observeExports(
        viewModel: PreferencesViewModel,
        initialState: Lifecycle.State = Lifecycle.State.STARTED,
    ): ObservedExports {
        val lifecycleDispatcher = UnconfinedTestDispatcher(coroutinesTestRule.testDispatcher.scheduler)
        val owner = TestLifecycleOwner(initialState, lifecycleDispatcher)
        val results = mutableListOf<ExportResult>()
        observeExportResults(viewModel.exportResults, owner) { result -> results.add(result) }
        advanceUntilIdle()
        return ObservedExports(owner, results)
    }

    private data class ObservedDecisions(
        val owner: TestLifecycleOwner,
        val decisions: List<LogoutDecision>,
    )

    private data class ObservedExports(
        val owner: TestLifecycleOwner,
        val results: List<ExportResult>,
    )
}
