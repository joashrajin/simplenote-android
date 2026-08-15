package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.util.Calendar

@ExperimentalCoroutinesApi
class InfoBottomSheetViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val notesRepository = mock<NotesRepository>()
    private val viewModel by lazy { InfoBottomSheetViewModel(notesRepository) }

    private fun advanceUntilIdle() = coroutinesTestRule.testDispatcher.scheduler.advanceUntilIdle()

    private fun runCurrent() = coroutinesTestRule.testDispatcher.scheduler.runCurrent()

    @Test
    fun loadReferencesPublishesTheRequestedRowsUnchanged() {
        val references = listOf(reference("source-1"), reference("source-2"))
        notesRepository.stub {
            onBlocking { referencesTo("target") }.doReturn(references)
        }

        val requestId = viewModel.loadReferences("target")
        assertEquals(ReferenceState.Loading(requestId, "target"), viewModel.referenceState.value)
        advanceUntilIdle()

        assertEquals(ReferenceState.Loaded(requestId, "target", references), viewModel.referenceState.value)
    }

    @Test
    fun aNewerRequestWinsWhenThePreviousRepositoryCallSwallowsCancellation() {
        val firstGate = CompletableDeferred<List<NoteReference>>()
        val firstReferences = listOf(reference("first-source"))
        val secondReferences = listOf(reference("second-source"))
        notesRepository.stub {
            onBlocking { referencesTo("first") }.doSuspendableAnswer {
                try {
                    firstGate.await()
                } catch (exception: CancellationException) {
                    withContext(NonCancellable) { firstGate.await() }
                }
            }
            onBlocking { referencesTo("second") }.doReturn(secondReferences)
        }

        viewModel.loadReferences("first")
        runCurrent()
        val secondRequest = viewModel.loadReferences("second")
        advanceUntilIdle()
        firstGate.complete(firstReferences)
        advanceUntilIdle()

        assertEquals(
            ReferenceState.Loaded(secondRequest, "second", secondReferences),
            viewModel.referenceState.value,
        )
    }

    @Test
    fun cancelRejectsALateResultAndClearsTheVisibleState() {
        val gate = CompletableDeferred<List<NoteReference>>()
        notesRepository.stub {
            onBlocking { referencesTo("target") }.doSuspendableAnswer {
                try {
                    gate.await()
                } catch (exception: CancellationException) {
                    withContext(NonCancellable) { gate.await() }
                }
            }
        }

        val requestId = viewModel.loadReferences("target")
        runCurrent()
        viewModel.cancelReferences(requestId)
        gate.complete(listOf(reference("late-source")))
        advanceUntilIdle()

        assertSame(ReferenceState.Idle, viewModel.referenceState.value)
    }

    @Test
    fun clearingTheViewModelRejectsALateResult() {
        val gate = CompletableDeferred<List<NoteReference>>()
        notesRepository.stub {
            onBlocking { referencesTo("target") }.doSuspendableAnswer {
                try {
                    gate.await()
                } catch (exception: CancellationException) {
                    withContext(NonCancellable) { gate.await() }
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
                InfoBottomSheetViewModel(notesRepository) as T
        }
        val storedViewModel = ViewModelProvider(owner, factory)[InfoBottomSheetViewModel::class.java]

        storedViewModel.loadReferences("target")
        runCurrent()
        store.clear()
        gate.complete(listOf(reference("late-source")))
        advanceUntilIdle()

        assertSame(ReferenceState.Idle, storedViewModel.referenceState.value)
    }

    @Test
    fun repositoryFailureClearsRowsAndTheNextRequestCanRecover() {
        val references = listOf(reference("source"))
        var calls = 0
        notesRepository.stub {
            onBlocking { referencesTo("target") }.doSuspendableAnswer {
                calls++
                if (calls == 1) throw IllegalStateException("query failed") else references
            }
        }

        viewModel.loadReferences("target")
        advanceUntilIdle()

        val failedState = viewModel.referenceState.value as ReferenceState.Error
        assertEquals("target", failedState.noteKey)

        val recoveredRequest = viewModel.loadReferences("target")
        advanceUntilIdle()

        assertEquals(
            ReferenceState.Loaded(recoveredRequest, "target", references),
            viewModel.referenceState.value,
        )
    }

    @Test
    fun reloadingTheSameNoteClearsThePreviousRowsBeforeLookingUpAgain() {
        val secondGate = CompletableDeferred<List<NoteReference>>()
        val firstReferences = listOf(reference("first-source"))
        val secondReferences = listOf(reference("second-source"))
        var calls = 0
        notesRepository.stub {
            onBlocking { referencesTo("target") }.doSuspendableAnswer {
                calls++
                if (calls == 1) firstReferences else secondGate.await()
            }
        }

        viewModel.loadReferences("target")
        advanceUntilIdle()
        assertEquals(firstReferences, (viewModel.referenceState.value as ReferenceState.Loaded).references)

        val secondRequest = viewModel.loadReferences("target")
        assertEquals(ReferenceState.Loading(secondRequest, "target"), viewModel.referenceState.value)
        secondGate.complete(secondReferences)
        advanceUntilIdle()

        assertEquals(
            ReferenceState.Loaded(secondRequest, "target", secondReferences),
            viewModel.referenceState.value,
        )
        assertEquals(2, calls)
    }

    @Test
    fun cancellationFromAnOlderSameNoteRequestCannotCancelItsReplacement() {
        val firstGate = CompletableDeferred<List<NoteReference>>()
        val secondGate = CompletableDeferred<List<NoteReference>>()
        val secondReferences = listOf(reference("second-source"))
        var calls = 0
        notesRepository.stub {
            onBlocking { referencesTo("target") }.doSuspendableAnswer {
                calls++
                if (calls == 1) {
                    try {
                        firstGate.await()
                    } catch (exception: CancellationException) {
                        withContext(NonCancellable) { firstGate.await() }
                    }
                } else {
                    secondGate.await()
                }
            }
        }

        val firstRequest = viewModel.loadReferences("target")
        runCurrent()
        val secondRequest = viewModel.loadReferences("target")
        runCurrent()
        viewModel.cancelReferences(firstRequest)
        secondGate.complete(secondReferences)
        firstGate.complete(emptyList())
        advanceUntilIdle()

        assertEquals(
            ReferenceState.Loaded(secondRequest, "target", secondReferences),
            viewModel.referenceState.value,
        )
    }

    private fun reference(key: String) = NoteReference(
        key = key,
        title = "Title $key",
        date = Calendar.getInstance(),
        count = 2,
    )
}
