package com.automattic.simplenote.repositories

import android.database.sqlite.SQLiteException
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SearchQueryBuilder
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import com.simperium.client.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.ArrayDeque
import java.util.Calendar
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class SimperiumNotesRepositoryTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private lateinit var notesBucket: Bucket<Note>
    private lateinit var cursor: Bucket.ObjectCursor<Note>
    private lateinit var repository: SimperiumNotesRepository

    @Before
    fun setUp() {
        notesBucket = mock()
        cursor = mock()
        whenever(notesBucket.query()).thenAnswer { Query<Note>(notesBucket) }
        whenever(notesBucket.searchObjects(any())).thenReturn(cursor)
        repository = SimperiumNotesRepository(notesBucket, SearchQueryBuilder(), coroutinesTestRule.testDispatcher)
    }

    private fun searchRequest(
        filter: NoteFilter = NoteFilter.AllNotes,
        rawSearch: String? = null,
        sort: SortOrder = SortOrder.MODIFIED_DESC,
        pinnedFirst: Boolean = true,
    ) = NoteSearchRequest(filter, rawSearch, sort, pinnedFirst)

    private fun executedQuery(): Query<Note> = argumentCaptor<Query<Note>>().run {
        verify(notesBucket, atLeastOnce()).searchObjects(capture())
        lastValue
    }

    private fun conditionsOf(query: Query<Note>): List<String> = query.conditions.map { condition ->
        "${condition.key} ${condition.comparisonType.name} ${condition.subject}"
    }

    private fun includesOf(query: Query<Note>): List<String> = query.fields.map { field ->
        when (field) {
            is Query.FullTextOffsets -> "offsets:${field.name}"
            is Query.FullTextSnippet -> "snippet:${field.name}<-${field.columnName}"
            else -> field.name
        }
    }

    private fun sortersOf(query: Query<Note>): List<String> =
        query.sorters.map { sorter -> "${sorter.key} ${sorter.type.name}" }

    @Test
    fun searchReturnsNotesCarryingTheCursorAndSearchSnapshot() = runTest {
        val result = repository.search(searchRequest(rawSearch = "hello"))

        val notes = result as NoteQueryResult.Notes
        assertSame(cursor, notes.cursor)
        assertEquals("hello", notes.searchSnapshot)
        verify(cursor, never()).close()
    }

    @Test
    fun searchWithoutSearchStringCarriesTheNullSnapshot() = runTest {
        val notes = repository.search(searchRequest(rawSearch = null)) as NoteQueryResult.Notes

        assertNull(notes.searchSnapshot)
    }

    @Test
    fun searchCancellationAtTheCallerHandoffClosesTheCursor() = runTest {
        val ioDispatcher = QueueingDispatcher()
        val handoffRepository = SimperiumNotesRepository(notesBucket, SearchQueryBuilder(), ioDispatcher)

        val job = launch {
            handoffRepository.search(searchRequest(rawSearch = "hello"))
        }
        runCurrent()
        ioDispatcher.runAll()
        job.cancel()
        runCurrent()

        verify(cursor).close()
    }

    @Test
    fun searchFailureAtTheCallerHandoffDoesNotSwallowCancellation() = runTest {
        val ioDispatcher = QueueingDispatcher()
        val handoffRepository = SimperiumNotesRepository(notesBucket, SearchQueryBuilder(), ioDispatcher)
        whenever(cursor.count).thenThrow(SQLiteException())
        var reachedAfterSearch = false

        val job = launch {
            handoffRepository.search(searchRequest(rawSearch = "\""))
            reachedAfterSearch = true
        }
        runCurrent()
        ioDispatcher.runAll()
        job.cancel()
        runCurrent()

        assertFalse(reachedAfterSearch)
        verify(cursor).close()
    }

    @Test
    fun searchExecutesTheSeamQueryShape() = runTest {
        repository.search(searchRequest(filter = NoteFilter.InTag("work"), rawSearch = "hello"))

        val query = executedQuery()
        assertEquals(
            listOf("deleted NOT_EQUAL_TO true", "tags EQUAL_TO work", "null MATCH hello*"),
            conditionsOf(query)
        )
        assertEquals(
            listOf(
                "offsets:match_offsets",
                "snippet:matchedTitle<-title",
                "snippet:matchedContent<-content",
                "title",
                "contentPreview",
                "pinned",
            ),
            includesOf(query)
        )
        assertEquals(listOf("pinned DESCENDING", "modified DESCENDING"), sortersOf(query))
    }

    @Test
    fun searchFailingOnExecuteReturnsInvalidQuery() = runTest {
        whenever(notesBucket.searchObjects(any())).thenThrow(SQLiteException())

        val result = repository.search(searchRequest(rawSearch = "\""))

        assertEquals(NoteQueryResult.InvalidQuery, result)
    }

    @Test
    fun searchFailingOnLazyFillReturnsInvalidQueryAndClosesTheCursor() = runTest {
        whenever(cursor.count).thenThrow(SQLiteException())

        val result = repository.search(searchRequest(rawSearch = "\""))

        assertEquals(NoteQueryResult.InvalidQuery, result)
        verify(cursor).close()
    }

    @Test
    fun getNoteReturnsTheBucketObject() = runTest {
        val note = Note("key1")
        whenever(notesBucket.get("key1")).thenReturn(note)

        assertSame(note, repository.getNote("key1"))
    }

    @Test
    fun getNoteMissingFromTheBucketReturnsNull() = runTest {
        whenever(notesBucket.get("missing")).thenThrow(BucketObjectMissingException())

        assertNull(repository.getNote("missing"))
    }

    @Test
    fun trashedNoteCountCountsTheAllDeletedQuery() = runTest {
        whenever(notesBucket.count(any<Query<Note>>())).thenReturn(3)

        assertEquals(3, repository.trashedNoteCount())

        val captor = argumentCaptor<Query<Note>>()
        verify(notesBucket).count(captor.capture())
        assertEquals(listOf("deleted EQUAL_TO true"), conditionsOf(captor.firstValue))
        assertEquals(emptyList<String>(), sortersOf(captor.firstValue))
    }

    @Test
    fun interlinkSuggestionsReproduceTheEditorAutocompleteShape() = runTest {
        val notes = repository.interlinkSuggestions("meeting", SortOrder.MODIFIED_DESC) as NoteQueryResult.Notes

        assertSame(cursor, notes.cursor)
        assertNull(notes.searchSnapshot)
        verify(cursor, never()).close()
        val query = executedQuery()
        assertEquals(listOf("deleted NOT_EQUAL_TO true", "title LIKE %meeting%"), conditionsOf(query))
        assertEquals(listOf("pinned", "title"), includesOf(query))
        assertEquals(listOf("pinned DESCENDING", "modified DESCENDING"), sortersOf(query))
    }

    @Test
    fun interlinkCancellationAtTheCallerHandoffClosesTheCursor() = runTest {
        val ioDispatcher = QueueingDispatcher()
        val handoffRepository = SimperiumNotesRepository(notesBucket, SearchQueryBuilder(), ioDispatcher)

        val job = launch {
            handoffRepository.interlinkSuggestions("meeting", SortOrder.MODIFIED_DESC)
        }
        runCurrent()
        ioDispatcher.runAll()
        job.cancel()
        runCurrent()

        verify(cursor).close()
    }

    @Test
    fun interlinkSuggestionsCloseTheCursorWhenLazyFillFails() = runTest {
        val failure = IllegalStateException("count failed")
        whenever(cursor.count).thenThrow(failure)

        val thrown = try {
            repository.interlinkSuggestions("meeting", SortOrder.MODIFIED_DESC)
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        assertSame(failure, thrown)
        verify(cursor).close()
    }

    @Test
    fun interlinkSuggestionsPreserveThePrimaryFailureWhenClosingAlsoFails() = runTest {
        val failure = IllegalStateException("count failed")
        val closeFailure = IllegalArgumentException("close failed")
        whenever(cursor.count).thenThrow(failure)
        whenever(cursor.close()).thenThrow(closeFailure)

        val thrown = try {
            repository.interlinkSuggestions("meeting", SortOrder.MODIFIED_DESC)
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        assertSame(failure, thrown)
        assertEquals(listOf(closeFailure), thrown?.suppressed?.toList())
    }

    @Test
    fun interlinkSuggestionsEmitOneSorterPerSortOrderAfterPinned() = runTest {
        val expectedSorters = mapOf(
            SortOrder.MODIFIED_DESC to "modified DESCENDING",
            SortOrder.MODIFIED_ASC to "modified ASCENDING",
            SortOrder.CREATED_DESC to "created DESCENDING",
            SortOrder.CREATED_ASC to "created ASCENDING",
            SortOrder.CONTENT_ASC to "content ASCENDING",
            SortOrder.CONTENT_DESC to "content DESCENDING",
        )

        for (sort in SortOrder.values()) {
            repository.interlinkSuggestions("meeting", sort)

            val query = executedQuery()
            assertEquals(sort.name, listOf("pinned DESCENDING", expectedSorters.getValue(sort)), sortersOf(query))
        }
    }

    @Test
    fun referencesToMapsRowsAndClosesTheCursor() = runTest {
        val date = Calendar.getInstance()
        val referencing = mock<Note>()
        whenever(referencing.simperiumKey).thenReturn("ref1")
        whenever(referencing.title).thenReturn("Meeting notes")
        whenever(referencing.modificationDate).thenReturn(date)
        whenever(referencing.content).thenReturn("simplenote://note/abc123 and again simplenote://note/abc123")
        whenever(cursor.moveToNext()).thenReturn(true, false)
        whenever(cursor.getObject()).thenReturn(referencing)

        val references = repository.referencesTo("abc123")

        assertEquals(listOf(NoteReference("ref1", "Meeting notes", date, 2)), references)
        verify(cursor).close()
        assertEquals(
            listOf("deleted NOT_EQUAL_TO true", "content LIKE %simplenote://note/abc123%"),
            conditionsOf(executedQuery())
        )
    }

    @Test
    fun referencesToWithNoReferencingNotesReturnsEmptyAndClosesTheCursor() = runTest {
        whenever(cursor.moveToNext()).thenReturn(false)

        assertEquals(emptyList<NoteReference>(), repository.referencesTo("abc123"))
        verify(cursor).close()
    }

    @Test
    fun hasUnsyncedNotesWhenANoteIsNewOrModified() = runTest {
        val allCursor = allObjectsCursor(noteMock(), noteMock(isModified = true))

        assertTrue(repository.hasUnsyncedNotes())
        verify(allCursor).close()
    }

    @Test
    fun hasUnsyncedNotesWhenANoteIsNewButUnmodified() = runTest {
        allObjectsCursor(noteMock(isNew = true))

        assertTrue(repository.hasUnsyncedNotes())
    }

    @Test
    fun hasUnsyncedNotesWhenEveryNoteIsSyncedIsFalse() = runTest {
        val allCursor = allObjectsCursor(noteMock(), noteMock())

        assertFalse(repository.hasUnsyncedNotes())
        verify(allCursor).close()
    }

    @Test
    fun allNotesForExportReturnsEveryBucketObjectInCursorOrderAndClosesTheCursor() = runTest {
        val note1 = Note("a")
        val note2 = Note("b")
        val allCursor = allObjectsCursor(note1, note2)

        assertEquals(listOf(note1, note2), repository.allNotesForExport())
        verify(allCursor).close()
    }

    private fun noteMock(isNew: Boolean = false, isModified: Boolean = false): Note {
        val note = mock<Note>()
        whenever(note.isNew).thenReturn(isNew)
        whenever(note.isModified).thenReturn(isModified)
        return note
    }

    private fun allObjectsCursor(vararg notes: Note): Bucket.ObjectCursor<Note> {
        val allCursor = mock<Bucket.ObjectCursor<Note>>()
        whenever(notesBucket.allObjects()).thenReturn(allCursor)
        val moves = notes.map { true } + false
        whenever(allCursor.moveToNext()).thenReturn(moves.first(), *moves.drop(1).toTypedArray())
        if (notes.isNotEmpty()) {
            whenever(allCursor.getObject()).thenReturn(notes.first(), *notes.drop(1).toTypedArray())
        }
        return allCursor
    }

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
