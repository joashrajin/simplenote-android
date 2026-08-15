package com.automattic.simplenote.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.usecases.LoadNoteWidgetPickerUseCase
import com.automattic.simplenote.usecases.WidgetNotePickerItem
import com.automattic.simplenote.usecases.WidgetNotePickerSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

@ExperimentalCoroutinesApi
class NoteWidgetPickerViewModelTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val useCase = mock<LoadNoteWidgetPickerUseCase>()

    private fun runCurrent() = coroutinesTestRule.testDispatcher.scheduler.runCurrent()

    private fun advanceUntilIdle() = coroutinesTestRule.testDispatcher.scheduler.advanceUntilIdle()

    @Test
    fun loadIsIdempotentAndPublishesContent() {
        val items = listOf(WidgetNotePickerItem("a", "Title", "Preview"))
        wheneverBlocking { useCase.loadItems() }.thenReturn(items)
        val viewModel = NoteWidgetPickerViewModel(useCase)

        viewModel.load()
        viewModel.load()
        assertTrue(viewModel.uiState.value is NoteWidgetPickerUiState.Loading)
        advanceUntilIdle()

        assertEquals(items, (viewModel.uiState.value as NoteWidgetPickerUiState.Content).items)
        verifyBlocking(useCase, times(1)) { loadItems() }
    }

    @Test
    fun aLoadFailurePublishesFailed() {
        val failure = IllegalStateException("load failed")
        wheneverBlocking { useCase.loadItems() }.thenThrow(failure)
        val viewModel = NoteWidgetPickerViewModel(useCase)

        viewModel.load()
        advanceUntilIdle()

        assertSame(failure, (viewModel.uiState.value as NoteWidgetPickerUiState.Failed).cause)
    }

    @Test
    fun rapidSelectionsLoadOnlyTheFirstTappedKey() {
        val gate = CompletableDeferred<WidgetNotePickerSelection?>()
        val selected = selection("a")
        wheneverBlocking { useCase.loadSelection("a") }.doSuspendableAnswer { gate.await() }
        val viewModel = NoteWidgetPickerViewModel(useCase)

        viewModel.selectNote("a")
        viewModel.selectNote("b")
        runCurrent()
        gate.complete(selected)
        advanceUntilIdle()

        val state = viewModel.selectionState.value as NoteWidgetPickerSelectionState.Selected
        assertSame(selected, state.selection)
        verifyBlocking(useCase) { loadSelection("a") }
        verifyBlocking(useCase, never()) { loadSelection("b") }
    }

    @Test
    fun aSelectedNoteIsClearedOnlyByItsMatchingConsumption() {
        wheneverBlocking { useCase.loadSelection("a") }.thenReturn(selection("a"))
        val viewModel = NoteWidgetPickerViewModel(useCase)
        viewModel.selectNote("a")
        advanceUntilIdle()
        val selected = viewModel.selectionState.value as NoteWidgetPickerSelectionState.Selected

        viewModel.consumeSelection(selected.requestId + 1)
        assertSame(selected, viewModel.selectionState.value)

        viewModel.consumeSelection(selected.requestId)
        assertTrue(viewModel.selectionState.value is NoteWidgetPickerSelectionState.Idle)
    }

    @Test
    fun aMissingSelectionPublishesAConsumableMissingState() {
        wheneverBlocking { useCase.loadSelection("missing") }.thenReturn(null)
        val viewModel = NoteWidgetPickerViewModel(useCase)

        viewModel.selectNote("missing")
        advanceUntilIdle()

        val missing = viewModel.selectionState.value as NoteWidgetPickerSelectionState.Missing
        assertEquals("missing", missing.key)
        viewModel.consumeSelection(missing.requestId)
        assertTrue(viewModel.selectionState.value is NoteWidgetPickerSelectionState.Idle)
    }

    @Test
    fun aSelectionFailureIsClearedOnlyByItsMatchingConsumption() {
        val failure = IllegalStateException("selection failed")
        wheneverBlocking { useCase.loadSelection("a") }.thenThrow(failure)
        val viewModel = NoteWidgetPickerViewModel(useCase)

        viewModel.selectNote("a")
        advanceUntilIdle()

        val failed = viewModel.selectionState.value as NoteWidgetPickerSelectionState.Failed
        assertEquals("a", failed.key)
        assertSame(failure, failed.cause)
        viewModel.consumeSelection(failed.requestId + 1)
        assertSame(failed, viewModel.selectionState.value)
        viewModel.consumeSelection(failed.requestId)
        assertTrue(viewModel.selectionState.value is NoteWidgetPickerSelectionState.Idle)
    }

    @Test
    fun clearingTheOwnerRejectsACancellationSurvivingLateLoad() {
        val gate = CompletableDeferred<Unit>()
        val late = listOf(WidgetNotePickerItem("late", "Late", "Late"))
        wheneverBlocking { useCase.loadItems() }.doSuspendableAnswer {
            try {
                gate.await()
                late
            } catch (_: CancellationException) {
                late
            }
        }
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, factory(useCase))[NoteWidgetPickerViewModel::class.java]
        viewModel.load()
        runCurrent()

        store.clear()
        runCurrent()

        assertTrue(viewModel.uiState.value is NoteWidgetPickerUiState.Loading)
    }

    @Test
    fun clearingTheOwnerRejectsACancellationSurvivingSelection() {
        val gate = CompletableDeferred<Unit>()
        val late = selection("a")
        wheneverBlocking { useCase.loadSelection("a") }.doSuspendableAnswer {
            try {
                gate.await()
                late
            } catch (_: CancellationException) {
                late
            }
        }
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, factory(useCase))[NoteWidgetPickerViewModel::class.java]
        viewModel.selectNote("a")
        runCurrent()

        store.clear()
        runCurrent()

        assertTrue(viewModel.selectionState.value is NoteWidgetPickerSelectionState.Loading)
    }

    private fun selection(key: String) = WidgetNotePickerSelection(
        key = key,
        title = "Title",
        content = "Title\nBody",
        markdownEnabled = true,
        previewEnabled = false,
    )

    private fun factory(useCase: LoadNoteWidgetPickerUseCase) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteWidgetPickerViewModel(useCase) as T
    }
}
