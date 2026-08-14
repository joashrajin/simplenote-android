package com.automattic.simplenote.repositories

import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.simperium.client.Bucket
import com.simperium.client.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
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
}
