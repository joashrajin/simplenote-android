package com.automattic.simplenote.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class NoteMarkdownViewModelTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private val repository = FakeNotesRepository()

    @Test
    fun loadingIsImmediateAndUsesTheRequestedKey() = runTest {
        val gate = CompletableDeferred<Note?>()
        repository.responses.addLast { gate.await() }
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")

        val state = viewModel.uiState.value as NoteMarkdownState.Loading
        assertEquals("a", state.noteKey)
        assertEquals(listOf("a"), repository.lookups)
    }

    @Test
    fun theRepositoryNoteIsDeliveredUnchanged() = runTest {
        val note = note("a", "first")
        repository.notes["a"] = note
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")
        runCurrent()

        val state = viewModel.uiState.value as NoteMarkdownState.Loaded
        assertEquals("a", state.noteKey)
        assertSame(note, state.note)
    }

    @Test
    fun laterRepositorySnapshotsReplaceTheLoadedNote() = runTest {
        val first = note("a", "first")
        val refreshed = note("a", "refreshed")
        val updates = MutableSharedFlow<Note?>(replay = 1)
        updates.tryEmit(first)
        repository.notes["a"] = first
        repository.observationFlows.addLast(updates)
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")
        runCurrent()
        updates.emit(refreshed)
        runCurrent()

        assertSame(refreshed, (viewModel.uiState.value as NoteMarkdownState.Loaded).note)
    }

    @Test
    fun repeatedSnapshotOfTheSameNoteInstancePublishesFreshState() = runTest {
        val note = note("a", "first")
        val updates = MutableSharedFlow<Note?>(replay = 1)
        updates.tryEmit(note)
        repository.observationFlows.addLast(updates)
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")
        runCurrent()
        val firstState = viewModel.uiState.value as NoteMarkdownState.Loaded
        updates.emit(note)
        runCurrent()
        val secondState = viewModel.uiState.value as NoteMarkdownState.Loaded

        assertTrue(secondState.stateId > firstState.stateId)
        assertSame(note, secondState.note)
    }

    @Test
    fun aRepositoryDeletionPublishesMissing() = runTest {
        val initial = note("a", "first")
        val updates = MutableSharedFlow<Note?>(replay = 1)
        updates.tryEmit(initial)
        repository.notes["a"] = initial
        repository.observationFlows.addLast(updates)
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")
        runCurrent()
        updates.emit(null)
        runCurrent()

        assertTrue(viewModel.uiState.value is NoteMarkdownState.Missing)
    }

    @Test
    fun aMissingNotePublishesMissing() = runTest {
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("missing")
        runCurrent()

        assertTrue(viewModel.uiState.value is NoteMarkdownState.Missing)
    }

    @Test
    fun aFailedLoadCanRetryTheSameKey() = runTest {
        val failure = IllegalStateException("read failed")
        val recovered = note("a", "recovered")
        repository.responses.addLast { throw failure }
        repository.responses.addLast { recovered }
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")
        runCurrent()
        val error = viewModel.uiState.value as NoteMarkdownState.Error
        assertSame(failure, error.cause)

        viewModel.loadNote("a")
        runCurrent()

        assertSame(recovered, (viewModel.uiState.value as NoteMarkdownState.Loaded).note)
        assertEquals(listOf("a", "a"), repository.lookups)
    }

    @Test
    fun aNewerRequestWinsWhenTheOldReadSwallowsCancellation() = runTest {
        val blocked = CompletableDeferred<Unit>()
        val stale = note("a", "stale")
        val fresh = note("b", "fresh")
        repository.responses.addLast {
            try {
                blocked.await()
                stale
            } catch (_: CancellationException) {
                stale
            }
        }
        repository.responses.addLast { fresh }
        val viewModel = NoteMarkdownViewModel(repository)
        viewModel.loadNote("a")
        runCurrent()

        viewModel.loadNote("b")
        runCurrent()

        val state = viewModel.uiState.value as NoteMarkdownState.Loaded
        assertEquals("b", state.noteKey)
        assertSame(fresh, state.note)
        assertEquals(listOf("a", "b"), repository.lookups)
    }

    @Test
    fun repeatingTheSameKeyPerformsAFreshRead() = runTest {
        val first = note("a", "first")
        val second = note("a", "second")
        repository.responses.addLast { first }
        repository.responses.addLast { second }
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")
        runCurrent()
        viewModel.loadNote("a")
        runCurrent()

        assertSame(second, (viewModel.uiState.value as NoteMarkdownState.Loaded).note)
        assertEquals(listOf("a", "a"), repository.lookups)
    }

    @Test
    fun replacingTheKeyCancelsThePreviousObservation() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val first = note("a", "first")
        val second = note("b", "second")
        repository.observationFlows.addLast(flow {
            try {
                emit(first)
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        })
        repository.observationFlows.addLast(flowOf(second))
        val viewModel = NoteMarkdownViewModel(repository)

        viewModel.loadNote("a")
        runCurrent()
        viewModel.loadNote("b")
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertSame(second, (viewModel.uiState.value as NoteMarkdownState.Loaded).note)
    }

    @Test
    fun clearingTheOwnerCancelsTheActiveObservation() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        repository.observationFlows.addLast(flow {
            try {
                emit(note("a", "first"))
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        })
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, factory(repository))[NoteMarkdownViewModel::class.java]
        viewModel.loadNote("a")
        runCurrent()

        store.clear()
        runCurrent()

        assertTrue(cancelled.isCompleted)
    }

    @Test
    fun clearingTheOwnerRejectsACancellationSurvivingLateResult() = runTest {
        val blocked = CompletableDeferred<Unit>()
        val late = note("a", "late")
        repository.responses.addLast {
            try {
                blocked.await()
                late
            } catch (_: CancellationException) {
                late
            }
        }
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, factory(repository))[NoteMarkdownViewModel::class.java]
        viewModel.loadNote("a")
        runCurrent()

        store.clear()
        runCurrent()

        assertTrue(viewModel.uiState.value is NoteMarkdownState.Loading)
    }

    private fun note(key: String, content: String) = Note(key).apply {
        setContent(content)
    }

    private fun factory(repository: NotesRepository) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteMarkdownViewModel(repository) as T
    }

    private class FakeNotesRepository : NotesRepository {
        val notes = mutableMapOf<String, Note>()
        val lookups = mutableListOf<String>()
        val responses = ArrayDeque<suspend () -> Note?>()
        val observationFlows = ArrayDeque<Flow<Note?>>()

        override suspend fun getNote(key: String): Note? {
            lookups.add(key)
            return responses.removeFirstOrNull()?.invoke() ?: notes[key]
        }

        override fun observeNote(key: String): Flow<Note?> =
            observationFlows.removeFirstOrNull() ?: flow { emit(getNote(key)) }

        override fun noteChanges(): Flow<NoteChange> = emptyFlow()
        override suspend fun search(request: NoteSearchRequest): NoteQueryResult = error("unused")
        override suspend fun trashedNoteCount(): Int = error("unused")
        override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult =
            error("unused")

        override suspend fun referencesTo(key: String): List<NoteReference> = error("unused")
        override suspend fun hasUnsyncedNotes(): Boolean = error("unused")
        override suspend fun allNotesForExport(): List<Note> = error("unused")
        override suspend fun createNote(content: String, key: String?): Note = error("unused")
        override suspend fun saveNote(note: Note) = error("unused")
        override suspend fun setTrashed(keys: List<String>, trashed: Boolean) = error("unused")
        override suspend fun emptyTrash() = error("unused")
        override suspend fun setPinned(keys: List<String>, pinned: Boolean) = error("unused")
        override suspend fun setPreviewEnabled(key: String, enabled: Boolean) = error("unused")
        override suspend fun setPublished(key: String, published: Boolean) = error("unused")
        override suspend fun getRevisions(key: String, max: Int): RevisionsResult = error("unused")
    }
}
