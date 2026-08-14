package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class NoteListViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val notesRepository = FakeNotesRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val viewModel = NoteListViewModel(notesRepository, preferencesRepository)

    private fun advanceUntilIdle() = coroutinesTestRule.testDispatcher.scheduler.advanceUntilIdle()

    private fun runCurrent() = coroutinesTestRule.testDispatcher.scheduler.runCurrent()

    @Test
    fun refreshBuildsAPinnedFirstRequestForEveryFilter() {
        preferencesRepository.sort = SortOrder.CREATED_ASC
        val filters = listOf(
            NoteFilter.AllNotes,
            NoteFilter.Trash,
            NoteFilter.Untagged,
            NoteFilter.InTag("shopping"),
        )

        for (filter in filters) {
            notesRepository.results.add(NoteQueryResult.InvalidQuery)
            viewModel.refreshList(filter, "welcome tag:x", false)
            advanceUntilIdle()
        }

        assertEquals(
            filters.map { filter ->
                NoteSearchRequest(filter, "welcome tag:x", SortOrder.CREATED_ASC, pinnedFirst = true)
            },
            notesRepository.requests
        )
        assertEquals(NoteFilter.InTag("shopping"), viewModel.filter)
    }

    @Test
    fun notesResultIsDeliveredWithTheNavFlagAndAOneShotSideEffectLatch() {
        val cursor = mock<Bucket.ObjectCursor<Note>>()
        val result = NoteQueryResult.Notes(cursor, null)
        notesRepository.results.add(result)

        viewModel.refreshList(NoteFilter.AllNotes, null, true)
        advanceUntilIdle()

        val update = requireNotNull(viewModel.noteList.value)
        assertSame(result, update.result)
        assertTrue(update.isFromNavSelect)
        assertTrue(update.consumeSideEffects())
        assertFalse("Side effects must run once per refresh delivery", update.consumeSideEffects())
    }

    @Test
    fun invalidQueryIsDeliveredForTheEmptyStateContract() {
        notesRepository.results.add(NoteQueryResult.InvalidQuery)

        viewModel.refreshList(NoteFilter.AllNotes, "t/", false)
        advanceUntilIdle()

        val update = requireNotNull(viewModel.noteList.value)
        assertSame(NoteQueryResult.InvalidQuery, update.result)
        assertFalse(update.isFromNavSelect)
    }

    @Test
    fun aNewerRefreshCancelsTheSuspendedOneAndOnlyTheNewerResultArrives() {
        val gate = CompletableDeferred<NoteQueryResult>()
        notesRepository.gates.add(gate)
        viewModel.refreshList(NoteFilter.AllNotes, null, false)
        runCurrent()
        assertNull("First refresh must still be suspended", viewModel.noteList.value)

        val secondResult = NoteQueryResult.Notes(mock<Bucket.ObjectCursor<Note>>(), null)
        notesRepository.results.add(secondResult)
        viewModel.refreshList(NoteFilter.Trash, null, false)
        advanceUntilIdle()

        assertEquals(1, notesRepository.cancelledSearches)
        assertSame(secondResult, requireNotNull(viewModel.noteList.value).result)

        // A late completion of the dead first search must change nothing.
        gate.complete(NoteQueryResult.InvalidQuery)
        advanceUntilIdle()
        assertSame(secondResult, requireNotNull(viewModel.noteList.value).result)
    }

    @Test
    fun aStaleResultThatSurvivesCancellationIsClosedAndNeverSwapped() {
        // Simulate a repository that swallows the cancellation and hands back its cursor anyway:
        // the sequence guard is the only thing standing between that cursor and the adapter.
        val staleCursor = mock<Bucket.ObjectCursor<Note>>()
        val gate = CompletableDeferred<NoteQueryResult>()
        notesRepository.gates.add(gate)
        notesRepository.resultSwallowingCancellation = NoteQueryResult.Notes(staleCursor, null)
        viewModel.refreshList(NoteFilter.AllNotes, null, false)
        runCurrent()

        val newerCursor = mock<Bucket.ObjectCursor<Note>>()
        val newerResult = NoteQueryResult.Notes(newerCursor, null)
        notesRepository.results.add(newerResult)
        viewModel.refreshList(NoteFilter.AllNotes, null, false)
        advanceUntilIdle()

        assertSame(newerResult, requireNotNull(viewModel.noteList.value).result)
        verify(staleCursor).close()
        verify(newerCursor, never()).close()
    }

    @Test
    fun bulkPinPartitionsAreDelegatedBeforeTheCompletionCallback() {
        val completions = mutableListOf<String>()
        notesRepository.onMutation = { completions.add(it) }

        viewModel.pinNotes(listOf("a", "b"), listOf("c")) { completions.add("complete") }
        advanceUntilIdle()

        assertEquals(listOf("setPinned([a, b], true)", "setPinned([c], false)", "complete"), completions)
    }

    @Test
    fun bulkTrashPartitionsAreDelegatedBeforeTheCompletionCallback() {
        val completions = mutableListOf<String>()
        notesRepository.onMutation = { completions.add(it) }

        viewModel.trashNotes(listOf("x"), listOf("y", "z")) { completions.add("complete") }
        advanceUntilIdle()

        assertEquals(listOf("setTrashed([x], true)", "setTrashed([y, z], false)", "complete"), completions)
    }

    private class FakeNotesRepository : NotesRepository {
        val requests = mutableListOf<NoteSearchRequest>()
        val results = ArrayDeque<NoteQueryResult>()
        val gates = ArrayDeque<CompletableDeferred<NoteQueryResult>>()
        var resultSwallowingCancellation: NoteQueryResult? = null
        var cancelledSearches = 0
        var onMutation: (String) -> Unit = {}

        override suspend fun search(request: NoteSearchRequest): NoteQueryResult {
            requests.add(request)
            val gate = gates.removeFirstOrNull() ?: return results.removeFirst()
            return try {
                gate.await()
            } catch (exception: CancellationException) {
                resultSwallowingCancellation ?: run {
                    cancelledSearches++
                    throw exception
                }
            }
        }

        override suspend fun setPinned(keys: List<String>, pinned: Boolean) {
            onMutation("setPinned($keys, $pinned)")
        }

        override suspend fun setTrashed(keys: List<String>, trashed: Boolean) {
            onMutation("setTrashed($keys, $trashed)")
        }

        override suspend fun getNote(key: String): Note? = null
        override suspend fun trashedNoteCount(): Int = 0
        override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult =
            NoteQueryResult.InvalidQuery

        override suspend fun referencesTo(key: String): List<NoteReference> = emptyList()
        override suspend fun hasUnsyncedNotes(): Boolean = false
        override suspend fun allNotesForExport(): List<Note> = emptyList()
        override suspend fun createNote(content: String, key: String?): Note = Note(key ?: "fake")
        override suspend fun saveNote(note: Note) = Unit
        override suspend fun emptyTrash() = Unit
        override suspend fun setPreviewEnabled(key: String, enabled: Boolean) = Unit
        override suspend fun setPublished(key: String, published: Boolean) = Unit
        override suspend fun getRevisions(key: String, max: Int): RevisionsResult = RevisionsResult.Failure
        override fun noteChanges(): Flow<NoteChange> = emptyFlow()
    }

    private class FakePreferencesRepository : PreferencesRepository {
        var sort: SortOrder = SortOrder.MODIFIED_DESC

        override suspend fun isAnalyticsEnabled(): Boolean = true
        override suspend fun setAnalyticsEnabled(enabled: Boolean) = Unit
        override fun analyticsEnabledSnapshot(): Boolean = true
        override suspend fun recentSearches(): List<String> = emptyList()
        override suspend fun addRecentSearch(query: String, index: Int) = Unit
        override suspend fun removeRecentSearch(query: String) = Unit
        override fun preferencesChanged(): Flow<Unit> = emptyFlow()
        override suspend fun sortOrder(): SortOrder = sort
    }
}
