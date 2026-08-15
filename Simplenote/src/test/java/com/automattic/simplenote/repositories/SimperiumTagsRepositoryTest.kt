package com.automattic.simplenote.repositories

import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.simperium.client.Bucket
import com.simperium.client.Query
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Characterization tests pinning [SimperiumTagsRepository.suggestTags] to the exact query
 * NoteListFragment.getTagSuggestions used to run inline: every tag ordered by note count
 * descending, the raw query contains-matched against tag names with LIKE, and a query ending
 * in the literal tag: prefix listing every tag unfiltered. Deliberately not searchTags — that
 * orders byKey for the tags screen.
 */
@ExperimentalCoroutinesApi
class SimperiumTagsRepositoryTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private lateinit var tagsBucket: Bucket<Tag>
    private lateinit var notesBucket: Bucket<Note>
    private lateinit var cursor: Bucket.ObjectCursor<Tag>
    private lateinit var repository: SimperiumTagsRepository

    @Before
    fun setUp() {
        tagsBucket = mock()
        notesBucket = mock()
        cursor = mock()
        whenever(tagsBucket.query()).thenAnswer { Query<Tag>(tagsBucket) }
        whenever(tagsBucket.searchObjects(any())).thenReturn(cursor)
        repository = SimperiumTagsRepository(tagsBucket, notesBucket, coroutinesTestRule.testDispatcher)
    }

    private fun cursorReturns(vararg tagKeys: String) {
        val moveResults = tagKeys.map { true } + false
        whenever(cursor.moveToNext())
            .thenReturn(moveResults.first(), *moveResults.drop(1).toTypedArray())
        if (tagKeys.isNotEmpty()) {
            val tags = tagKeys.map { key -> Tag(key) }
            whenever(cursor.getObject()).thenReturn(tags.first(), *tags.drop(1).toTypedArray())
        }
    }

    private fun executedQuery(): Query<Tag> = argumentCaptor<Query<Tag>>().run {
        verify(tagsBucket, atLeastOnce()).searchObjects(capture())
        lastValue
    }

    private fun conditionsOf(query: Query<Tag>): List<String> = query.conditions.map { condition ->
        "${condition.key} ${condition.comparisonType.name} ${condition.subject}"
    }

    private fun includesOf(query: Query<Tag>): List<String> = query.fields.map { field -> field.name }

    private fun sortersOf(query: Query<Tag>): List<String> =
        query.sorters.map { sorter -> "${sorter.key} ${sorter.type.name}" }

    @Test
    fun suggestTagsMatchesTheRawQueryAnywhereInTheNameOrderedByUse() = runTest {
        cursorReturns("grocery", "groceries")

        val names = repository.suggestTags("gro")

        assertEquals(listOf("grocery", "groceries"), names)
        val query = executedQuery()
        assertEquals(listOf("name LIKE %gro%"), conditionsOf(query))
        assertEquals(listOf("note_count DESCENDING"), sortersOf(query))
    }

    @Test
    fun suggestTagsWithATrailingTagPrefixListsEveryTag() = runTest {
        cursorReturns("kitchen", "work")

        val names = repository.suggestTags("tag:")

        assertEquals(listOf("kitchen", "work"), names)
        assertEquals(emptyList<String>(), conditionsOf(executedQuery()))
    }

    @Test
    fun suggestTagsTreatsAnyQueryEndingInTheTagPrefixAsUnfiltered() = runTest {
        cursorReturns()

        repository.suggestTags("grocery tag:")

        assertEquals(emptyList<String>(), conditionsOf(executedQuery()))
    }

    @Test
    fun suggestTagsFiltersOnTheRawQueryTextIncludingAnEmbeddedTagPrefix() = runTest {
        cursorReturns()

        repository.suggestTags("tag:g")

        // The legacy query never stripped the prefix: the raw text is LIKE-matched as-is.
        assertEquals(listOf("name LIKE %tag:g%"), conditionsOf(executedQuery()))
    }

    @Test
    fun suggestTagsClosesItsCursor() = runTest {
        cursorReturns("kitchen")

        repository.suggestTags("kit")

        verify(cursor).close()
    }

    @Test
    fun allTagsClosesItsCursor() = runTest {
        whenever(cursor.count).thenReturn(0)

        repository.allTags()

        verify(cursor).close()
    }

    @Test
    fun searchTagsClosesItsCursor() = runTest {
        whenever(cursor.count).thenReturn(0)

        repository.searchTags("kit")

        verify(cursor).close()
    }

    @Test
    fun searchTagsClosesItsCursorWhenMaterializationFails() = runTest {
        val failure = IllegalStateException("count failed")
        whenever(cursor.count).thenThrow(failure)

        val thrown = try {
            repository.searchTags("kit")
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        assertSame(failure, thrown)
        verify(cursor).close()
    }

    @Test
    fun allTagsClosesItsCursorWhenMaterializationFails() = runTest {
        val failure = IllegalStateException("count failed")
        whenever(cursor.count).thenThrow(failure)

        val thrown = try {
            repository.allTags()
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        assertSame(failure, thrown)
        verify(cursor).close()
    }

    @Test
    fun deleteTagClosesItsNotesCursorWhenNoteRemovalFails() = runTest {
        val tag = mock<Tag>()
        val note = mock<Note>()
        val notesCursor = mock<Bucket.ObjectCursor<Note>>()
        val failure = IllegalStateException("remove failed")
        whenever(tag.name).thenReturn("work")
        whenever(tag.findNotes(notesBucket, "work")).thenReturn(notesCursor)
        whenever(notesCursor.moveToNext()).thenReturn(true)
        whenever(notesCursor.getObject()).thenReturn(note)
        whenever(note.removeTag("work")).thenThrow(failure)

        val thrown = try {
            repository.deleteTag(tag)
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        assertSame(failure, thrown)
        verify(notesCursor).close()
        verify(tag, never()).delete()
    }

    @Test
    fun navigationTagsRegistersEveryListenerBeforeItsInitialQuery() = runTest {
        cursorReturns()

        repository.navigationTags(sortAlphabetically = false).first()

        inOrder(tagsBucket).apply {
            verify(tagsBucket).addOnSaveObjectListener(any())
            verify(tagsBucket).addOnDeleteObjectListener(any())
            verify(tagsBucket).addOnNetworkChangeListener(any())
            verify(tagsBucket).searchObjects(any())
        }
    }

    @Test
    fun navigationTagsPreservesManualOrderAndClosesItsCursor() = runTest {
        cursorReturns("work", "home")

        val tags = repository.navigationTags(sortAlphabetically = false).first()

        assertEquals(listOf("work", "home"), tags.map { tag -> tag.name })
        assertEquals(listOf("index ASCENDING", "null ASCENDING"), sortersOf(executedQuery()))
        assertEquals(listOf("name"), includesOf(executedQuery()))
        verify(cursor).close()
    }

    @Test
    fun navigationTagsPreservesAlphabeticalOrderAndClosesItsCursor() = runTest {
        cursorReturns("alpha", "Beta")

        val tags = repository.navigationTags(sortAlphabetically = true).first()

        assertEquals(listOf("alpha", "Beta"), tags.map { tag -> tag.name })
        assertEquals(listOf("LOWER(name) ASCENDING"), sortersOf(executedQuery()))
        assertEquals(listOf("name"), includesOf(executedQuery()))
        verify(cursor).close()
    }

    @Test
    fun navigationTagsClosesItsCursorWhenMaterializationFails() = runTest {
        val failure = IllegalStateException("object failed")
        whenever(cursor.moveToNext()).thenReturn(true)
        whenever(cursor.getObject()).thenThrow(failure)

        val thrown = try {
            repository.navigationTags(sortAlphabetically = false).first()
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        assertEquals(failure.message, thrown?.message)
        verify(cursor).close()
    }

    @Test
    fun navigationTagsRefreshesForEveryBucketChangeAndRemovesTheExactListeners() = runTest {
        whenever(cursor.moveToNext()).thenReturn(false)
        val snapshots = mutableListOf<List<Tag>>()
        val job = backgroundScope.launch {
            repository.navigationTags(sortAlphabetically = false).take(4).toList(snapshots)
        }
        runCurrent()

        val saveListener = argumentCaptor<Bucket.OnSaveObjectListener<Tag>>().run {
            verify(tagsBucket).addOnSaveObjectListener(capture())
            firstValue
        }
        val deleteListener = argumentCaptor<Bucket.OnDeleteObjectListener<Tag>>().run {
            verify(tagsBucket).addOnDeleteObjectListener(capture())
            firstValue
        }
        val networkListener = argumentCaptor<Bucket.OnNetworkChangeListener<Tag>>().run {
            verify(tagsBucket).addOnNetworkChangeListener(capture())
            firstValue
        }

        saveListener.onSaveObject(tagsBucket, Tag("saved"))
        runCurrent()
        deleteListener.onDeleteObject(tagsBucket, Tag("deleted"))
        runCurrent()
        networkListener.onNetworkChange(tagsBucket, Bucket.ChangeType.MODIFY, "remote")
        runCurrent()

        assertEquals(4, snapshots.size)
        job.join()
        verify(tagsBucket).removeOnSaveObjectListener(same(saveListener))
        verify(tagsBucket).removeOnDeleteObjectListener(same(deleteListener))
        verify(tagsBucket).removeOnNetworkChangeListener(same(networkListener))
    }

    @Test
    fun navigationTagsConflatesABurstBeforeRunningItsQueryOnIo() = runTest {
        val ioDispatcher = QueueingDispatcher()
        val queuedRepository = SimperiumTagsRepository(tagsBucket, notesBucket, ioDispatcher)
        whenever(cursor.moveToNext()).thenReturn(false)
        val snapshots = mutableListOf<List<Tag>>()
        val job = backgroundScope.launch {
            queuedRepository.navigationTags(sortAlphabetically = false).take(2).toList(snapshots)
        }
        runCurrent()

        val saveListener = argumentCaptor<Bucket.OnSaveObjectListener<Tag>>().run {
            verify(tagsBucket).addOnSaveObjectListener(capture())
            firstValue
        }
        val deleteListener = argumentCaptor<Bucket.OnDeleteObjectListener<Tag>>().run {
            verify(tagsBucket).addOnDeleteObjectListener(capture())
            firstValue
        }
        val networkListener = argumentCaptor<Bucket.OnNetworkChangeListener<Tag>>().run {
            verify(tagsBucket).addOnNetworkChangeListener(capture())
            firstValue
        }

        saveListener.onSaveObject(tagsBucket, Tag("saved"))
        deleteListener.onDeleteObject(tagsBucket, Tag("deleted"))
        networkListener.onNetworkChange(tagsBucket, Bucket.ChangeType.MODIFY, "remote")
        runCurrent()
        verify(tagsBucket, never()).searchObjects(any())

        repeat(6) {
            ioDispatcher.runAll()
            runCurrent()
        }
        assertEquals(1, snapshots.size)
        verify(tagsBucket).searchObjects(any())

        saveListener.onSaveObject(tagsBucket, Tag("later"))
        runCurrent()
        repeat(2) {
            ioDispatcher.runAll()
            runCurrent()
        }

        job.join()
        assertEquals(2, snapshots.size)
        verify(tagsBucket, times(2)).searchObjects(any())
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
