package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Suggestion
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val tagsRepository = FakeTagsRepository()

    // Lazy so the construction-time preferencesChanged() collection launches after the rule has
    // installed the test Main dispatcher; a field initializer would run before it.
    private val viewModel by lazy { NoteListViewModel(notesRepository, preferencesRepository, tagsRepository) }

    private fun advanceUntilIdle() = coroutinesTestRule.testDispatcher.scheduler.advanceUntilIdle()

    private fun runCurrent() = coroutinesTestRule.testDispatcher.scheduler.runCurrent()

    private fun suggestionPairs() = requireNotNull(viewModel.suggestions.value).suggestions
        .map { suggestion -> suggestion.name to suggestion.type }

    @Test
    fun refreshBuildsAPinnedFirstRequestForEveryFilter() {
        preferencesRepository.sort = SortOrder.CREATED_ASC
        viewModel.searchNotes("welcome tag:x", isSubmit = false)
        val filters = listOf(
            NoteFilter.AllNotes,
            NoteFilter.Trash,
            NoteFilter.Untagged,
            NoteFilter.InTag("shopping"),
        )

        for (filter in filters) {
            notesRepository.results.add(NoteQueryResult.InvalidQuery)
            viewModel.refreshList(filter, false)
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

        viewModel.refreshList(NoteFilter.AllNotes, true)
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
        viewModel.searchNotes("t/", isSubmit = false)

        viewModel.refreshList(NoteFilter.AllNotes, false)
        advanceUntilIdle()

        val update = requireNotNull(viewModel.noteList.value)
        assertSame(NoteQueryResult.InvalidQuery, update.result)
        assertFalse(update.isFromNavSelect)
    }

    @Test
    fun replacingAnUnconsumedDeliveryClosesItsCursor() {
        val firstCursor = mock<Bucket.ObjectCursor<Note>>()
        notesRepository.results.add(NoteQueryResult.Notes(firstCursor, null))
        viewModel.refreshList(NoteFilter.AllNotes, false)
        advanceUntilIdle()

        val secondCursor = mock<Bucket.ObjectCursor<Note>>()
        notesRepository.results.add(NoteQueryResult.Notes(secondCursor, null))
        viewModel.refreshList(NoteFilter.AllNotes, false)
        advanceUntilIdle()

        verify(firstCursor).close()
        verify(secondCursor, never()).close()
    }

    @Test
    fun replacingAConsumedDeliveryLeavesItsCursorToTheAdapter() {
        val firstCursor = mock<Bucket.ObjectCursor<Note>>()
        notesRepository.results.add(NoteQueryResult.Notes(firstCursor, null))
        viewModel.refreshList(NoteFilter.AllNotes, false)
        advanceUntilIdle()
        requireNotNull(viewModel.noteList.value).consumeSideEffects()

        notesRepository.results.add(NoteQueryResult.Notes(mock<Bucket.ObjectCursor<Note>>(), null))
        viewModel.refreshList(NoteFilter.AllNotes, false)
        advanceUntilIdle()

        verify(firstCursor, never()).close()
    }

    @Test
    fun aNewerRefreshCancelsTheSuspendedOneAndOnlyTheNewerResultArrives() {
        val gate = CompletableDeferred<NoteQueryResult>()
        notesRepository.gates.add(gate)
        viewModel.refreshList(NoteFilter.AllNotes, false)
        runCurrent()
        assertNull("First refresh must still be suspended", viewModel.noteList.value)

        val secondResult = NoteQueryResult.Notes(mock<Bucket.ObjectCursor<Note>>(), null)
        notesRepository.results.add(secondResult)
        viewModel.refreshList(NoteFilter.Trash, false)
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
        viewModel.refreshList(NoteFilter.AllNotes, false)
        runCurrent()

        val newerCursor = mock<Bucket.ObjectCursor<Note>>()
        val newerResult = NoteQueryResult.Notes(newerCursor, null)
        notesRepository.results.add(newerResult)
        viewModel.refreshList(NoteFilter.AllNotes, false)
        advanceUntilIdle()

        assertSame(newerResult, requireNotNull(viewModel.noteList.value).result)
        verify(staleCursor).close()
        verify(newerCursor, never()).close()
    }

    // The search path rides the same pipeline as the plain refresh path, the deterministic
    // replacement for the two legacy AsyncTask chains that never cancelled each other and let
    // whichever onPostExecute ran last win the adapter.

    @Test
    fun searchSubmitRoutesAnAllNotesUnpinnedRequestAndKeepsTheSelectedFilter() {
        preferencesRepository.sort = SortOrder.MODIFIED_ASC
        notesRepository.results.add(NoteQueryResult.InvalidQuery)
        viewModel.refreshList(NoteFilter.InTag("shopping"), false)
        advanceUntilIdle()

        notesRepository.results.add(NoteQueryResult.InvalidQuery)
        viewModel.searchNotes("hello tag:x", isSubmit = true)
        advanceUntilIdle()

        assertEquals(
            listOf(
                NoteSearchRequest(NoteFilter.InTag("shopping"), null, SortOrder.MODIFIED_ASC, pinnedFirst = true),
                // queryNotesForSearch spanned every non-trashed note whatever tag was selected,
                // carried the raw query, and dropped the pinned-first ordering.
                NoteSearchRequest(NoteFilter.AllNotes, "hello tag:x", SortOrder.MODIFIED_ASC, pinnedFirst = false),
            ),
            notesRepository.requests
        )
        assertEquals(NoteFilter.InTag("shopping"), viewModel.filter)
    }

    @Test
    fun searchWithoutSubmitIssuesNoListRefresh() {
        viewModel.searchNotes("hello", isSubmit = false)
        advanceUntilIdle()

        assertEquals(emptyList<NoteSearchRequest>(), notesRepository.requests)
        assertNull(viewModel.noteList.value)
        assertTrue(viewModel.isSearching)
        assertEquals("hello", viewModel.searchString)
    }

    @Test
    fun aSearchRefreshSupersedesAnInFlightPlainRefresh() {
        val gate = CompletableDeferred<NoteQueryResult>()
        notesRepository.gates.add(gate)
        viewModel.refreshList(NoteFilter.AllNotes, false)
        runCurrent()

        val searchResult = NoteQueryResult.Notes(mock<Bucket.ObjectCursor<Note>>(), "hello")
        notesRepository.results.add(searchResult)
        viewModel.searchNotes("hello", isSubmit = true)
        advanceUntilIdle()

        assertEquals(1, notesRepository.cancelledSearches)
        assertSame(searchResult, requireNotNull(viewModel.noteList.value).result)
    }

    @Test
    fun aPlainRefreshSupersedesAnInFlightSearchRefresh() {
        val gate = CompletableDeferred<NoteQueryResult>()
        notesRepository.gates.add(gate)
        viewModel.searchNotes("hello", isSubmit = true)
        runCurrent()

        val plainResult = NoteQueryResult.Notes(mock<Bucket.ObjectCursor<Note>>(), "hello")
        notesRepository.results.add(plainResult)
        viewModel.refreshList(NoteFilter.InTag("shopping"), false)
        advanceUntilIdle()

        assertEquals(1, notesRepository.cancelledSearches)
        assertSame(plainResult, requireNotNull(viewModel.noteList.value).result)
        // The live query text stays applied to plain refreshes until clearSearchQuery, exactly
        // as queryNotes always read mSearchString.
        assertEquals(
            NoteSearchRequest(NoteFilter.InTag("shopping"), "hello", SortOrder.MODIFIED_DESC, pinnedFirst = true),
            notesRepository.requests.last()
        )
    }

    @Test
    fun theDeliveredSnapshotTracksTheRequestNotTheLiveSearchState() {
        notesRepository.echoSearchSnapshot = true

        viewModel.searchNotes("hello", isSubmit = true)
        advanceUntilIdle()
        assertEquals(
            "hello",
            (requireNotNull(viewModel.noteList.value).result as NoteQueryResult.Notes).searchSnapshot
        )

        // The legacy clearSearch sequence: leave search mode, drop the query, refresh plain.
        viewModel.stopSearching()
        viewModel.clearSearchQuery()
        viewModel.refreshList(NoteFilter.AllNotes, false)
        advanceUntilIdle()

        assertNull((requireNotNull(viewModel.noteList.value).result as NoteQueryResult.Notes).searchSnapshot)
    }

    @Test
    fun searchStateFollowsTheLegacyFieldSemantics() {
        assertFalse(viewModel.isSearching)
        assertFalse(viewModel.hasSearchQuery())

        viewModel.searchNotes("", isSubmit = false)
        assertTrue(viewModel.isSearching)
        assertFalse("An empty query is not a search query", viewModel.hasSearchQuery())

        viewModel.searchNotes("hello", isSubmit = false)
        assertTrue(viewModel.hasSearchQuery())

        viewModel.stopSearching()
        assertFalse(viewModel.isSearching)
        assertTrue("Stopping search mode keeps the query until it is cleared", viewModel.hasSearchQuery())

        viewModel.clearSearchQuery()
        assertFalse(viewModel.hasSearchQuery())
        assertNull(viewModel.searchString)
    }

    @Test
    fun anEmptyQueryFeedsTheRecentSearches() {
        preferencesRepository.recents = listOf("alpha", "beta")

        viewModel.searchNotes("", isSubmit = false)
        advanceUntilIdle()

        assertTrue(requireNotNull(viewModel.suggestions.value).isRecentSearches)
        assertEquals(
            listOf("alpha" to Suggestion.Type.HISTORY, "beta" to Suggestion.Type.HISTORY),
            suggestionPairs()
        )
    }

    @Test
    fun aTypedQueryFeedsItselfFirstThenTheSuggestedTags() {
        tagsRepository.suggestions = listOf("grocery", "groceries")

        viewModel.searchNotes("gro", isSubmit = false)
        advanceUntilIdle()

        assertEquals("gro", tagsRepository.queries.single())
        assertFalse(requireNotNull(viewModel.suggestions.value).isRecentSearches)
        assertEquals(
            listOf(
                "gro" to Suggestion.Type.QUERY,
                "grocery" to Suggestion.Type.TAG,
                "groceries" to Suggestion.Type.TAG,
            ),
            suggestionPairs()
        )
    }

    @Test
    fun aNewerSuggestionLoadSupersedesTheInFlightOne() {
        val gate = CompletableDeferred<List<String>>()
        tagsRepository.gates.add(gate)
        viewModel.searchNotes("a", isSubmit = false)
        runCurrent()

        tagsRepository.suggestions = listOf("about")
        viewModel.searchNotes("ab", isSubmit = false)
        advanceUntilIdle()

        assertEquals(
            listOf("ab" to Suggestion.Type.QUERY, "about" to Suggestion.Type.TAG),
            suggestionPairs()
        )

        // A late completion of the superseded load must change nothing.
        gate.complete(listOf("aardvark"))
        advanceUntilIdle()
        assertEquals(
            listOf("ab" to Suggestion.Type.QUERY, "about" to Suggestion.Type.TAG),
            suggestionPairs()
        )
    }

    @Test
    fun aPreferencesChangeReloadsTheRecentSearches() {
        // The legacy preference-bucket listeners re-read the recents after every save, delete,
        // or remote change; the repository change stream reproduces that chain.
        preferencesRepository.recents = listOf("alpha")
        viewModel
        runCurrent()

        preferencesRepository.changes.tryEmit(Unit)
        advanceUntilIdle()

        assertTrue(requireNotNull(viewModel.suggestions.value).isRecentSearches)
        assertEquals(listOf("alpha" to Suggestion.Type.HISTORY), suggestionPairs())
    }

    @Test
    fun removingARecentSearchCapturesItsIndexForTheUndoRestore() {
        preferencesRepository.removeRecentSearchResult = 3

        viewModel.removeRecentSearch("beta")
        advanceUntilIdle()
        viewModel.restoreRemovedRecentSearch("beta")
        advanceUntilIdle()

        assertEquals(listOf("beta"), preferencesRepository.removedRecents)
        assertEquals(listOf("beta" to 3), preferencesRepository.addedRecents)
    }

    @Test
    fun theSubmitPathAddsTheRecentSearchAtTheTop() {
        viewModel.addRecentSearch("hello", 0)
        advanceUntilIdle()

        assertEquals(listOf("hello" to 0), preferencesRepository.addedRecents)
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
        var echoSearchSnapshot = false
        var cancelledSearches = 0
        var onMutation: (String) -> Unit = {}

        override suspend fun search(request: NoteSearchRequest): NoteQueryResult {
            requests.add(request)
            val gate = gates.removeFirstOrNull()
            if (gate == null) {
                if (echoSearchSnapshot) {
                    return NoteQueryResult.Notes(mock(), request.rawSearch)
                }
                return results.removeFirst()
            }
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
        var recents: List<String> = emptyList()
        var removeRecentSearchResult = -1
        val addedRecents = mutableListOf<Pair<String, Int>>()
        val removedRecents = mutableListOf<String>()
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override suspend fun isAnalyticsEnabled(): Boolean = true
        override suspend fun setAnalyticsEnabled(enabled: Boolean) = Unit
        override fun analyticsEnabledSnapshot(): Boolean = true
        override suspend fun recentSearches(): List<String> = recents

        override suspend fun addRecentSearch(query: String, index: Int) {
            addedRecents.add(query to index)
        }

        override suspend fun removeRecentSearch(query: String): Int {
            removedRecents.add(query)
            return removeRecentSearchResult
        }

        override fun preferencesChanged(): Flow<Unit> = changes
        override suspend fun sortOrder(): SortOrder = sort
    }

    private class FakeTagsRepository : TagsRepository {
        var suggestions: List<String> = emptyList()
        val queries = mutableListOf<String>()
        val gates = ArrayDeque<CompletableDeferred<List<String>>>()

        override suspend fun suggestTags(query: String): List<String> {
            queries.add(query)
            val gate = gates.removeFirstOrNull() ?: return suggestions
            return gate.await()
        }

        override fun saveTag(tagName: String): Boolean = false
        override fun isTagValid(tagName: String): Boolean = false
        override fun isTagMissing(tagName: String): Boolean = false
        override fun isTagConflict(tagName: String, oldTagName: String): Boolean = false
        override fun getCanonicalTagName(tagName: String): String = tagName
        override fun renameTag(tagName: String, oldTag: Tag): Boolean = false
        override suspend fun allTags(): List<TagItem> = emptyList()
        override suspend fun searchTags(query: String): List<TagItem> = emptyList()
        override suspend fun deleteTag(tag: Tag) = Unit
        override suspend fun tagsChanged(): Flow<Boolean> = emptyFlow()
    }
}
