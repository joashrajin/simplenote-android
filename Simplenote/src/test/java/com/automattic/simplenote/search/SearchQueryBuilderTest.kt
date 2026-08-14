package com.automattic.simplenote.search

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.utils.SearchTokenizer
import com.simperium.client.Bucket
import com.simperium.client.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Characterization tests pinning SearchQueryBuilder to the exact query shapes that
 * NoteListFragment.queryNotes, NoteListFragment.queryNotesForSearch, and
 * PrefUtils.sortNoteQuery build today. A failure here means the built query no longer
 * matches the legacy note list behavior.
 */
class SearchQueryBuilderTest {

    private lateinit var bucket: Bucket<Note>
    private val builder = SearchQueryBuilder()

    @Before
    fun setUp() {
        bucket = mock()
        whenever(bucket.query()).thenAnswer { Query<Note>(bucket) }
    }

    private fun build(
        filter: NoteFilter = NoteFilter.AllNotes,
        rawSearch: String? = null,
        sort: SortOrder = SortOrder.MODIFIED_DESC,
        pinnedFirst: Boolean = true,
    ): Query<Note> = builder.build(bucket, NoteSearchRequest(filter, rawSearch, sort, pinnedFirst))

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

    private fun fullTextMatch(remaining: String) =
        "null MATCH ${SearchTokenizer(remaining)}"

    // Filter shapes, as built by TagsAdapter.TagMenuItem.query() via the Note query helpers.

    @Test
    fun allNotesListShape() {
        val query = build(filter = NoteFilter.AllNotes)

        assertEquals(listOf("deleted NOT_EQUAL_TO true"), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview", "pinned"), includesOf(query))
        assertEquals(listOf("pinned DESCENDING", "modified DESCENDING"), sortersOf(query))
    }

    @Test
    fun trashListShape() {
        val query = build(filter = NoteFilter.Trash)

        assertEquals(listOf("deleted EQUAL_TO true"), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview", "pinned"), includesOf(query))
        assertEquals(listOf("pinned DESCENDING", "modified DESCENDING"), sortersOf(query))
    }

    @Test
    fun untaggedListShape() {
        val query = build(filter = NoteFilter.Untagged)

        assertEquals(listOf("deleted NOT_EQUAL_TO true", "tags EQUAL_TO null"), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview", "pinned"), includesOf(query))
        assertEquals(listOf("pinned DESCENDING", "modified DESCENDING"), sortersOf(query))
    }

    @Test
    fun inTagListShape() {
        val query = build(filter = NoteFilter.InTag("work"))

        assertEquals(listOf("deleted NOT_EQUAL_TO true", "tags EQUAL_TO work"), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview", "pinned"), includesOf(query))
        assertEquals(listOf("pinned DESCENDING", "modified DESCENDING"), sortersOf(query))
    }

    // Active search (queryNotesForSearch): all-notes shape, full text block, no pinned include or ordering.

    @Test
    fun activeSearchShape() {
        val query = build(rawSearch = "hello world", pinnedFirst = false)

        assertEquals(listOf("deleted NOT_EQUAL_TO true", "null MATCH hello* world*"), conditionsOf(query))
        assertEquals(
            listOf(
                "offsets:match_offsets",
                "snippet:matchedTitle<-title",
                "snippet:matchedContent<-content",
                "title",
                "contentPreview",
            ),
            includesOf(query)
        )
        assertEquals(listOf("modified DESCENDING"), sortersOf(query))
    }

    @Test
    fun fullTextMatchUsesNullKeyAndMatchComparison() {
        val match = build(rawSearch = "hello", pinnedFirst = false).conditions.last()

        assertNull(match.key)
        assertEquals(Query.ComparisonType.MATCH, match.comparisonType)
        assertEquals("hello*", match.subject.toString())
    }

    // Search inside a filter (queryNotes with a search string): keeps the filter, pinned include, and ordering.

    @Test
    fun searchWithinTagKeepsFilterAndPinnedOrdering() {
        val query = build(filter = NoteFilter.InTag("work"), rawSearch = "hello")

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
    fun searchWithinTrashKeepsDeletedCondition() {
        val query = build(filter = NoteFilter.Trash, rawSearch = "hello")

        assertEquals(listOf("deleted EQUAL_TO true", "null MATCH hello*"), conditionsOf(query))
    }

    @Test
    fun nullSearchOmitsFullTextBlock() {
        val query = build(rawSearch = null)

        assertEquals(listOf("deleted NOT_EQUAL_TO true"), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview", "pinned"), includesOf(query))
    }

    @Test
    fun emptySearchOmitsFullTextBlock() {
        val query = build(rawSearch = "")

        assertEquals(listOf("deleted NOT_EQUAL_TO true"), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview", "pinned"), includesOf(query))
    }

    @Test
    fun whitespaceSearchStillAddsFullTextMatch() {
        val query = build(rawSearch = "   ")

        assertEquals(listOf("deleted NOT_EQUAL_TO true", fullTextMatch("   ")), conditionsOf(query))
    }

    // tag: extraction, as NoteListFragment.queryTags performs it.

    @Test
    fun multipleTagTermsAndTogether() {
        val query = build(rawSearch = "tag:a tag:b note", pinnedFirst = false)

        assertEquals(
            listOf("deleted NOT_EQUAL_TO true", "tags LIKE a", "tags LIKE b", "null MATCH note*"),
            conditionsOf(query)
        )
    }

    @Test
    fun tagTermMixedWithFreeTextSearchesRemainder() {
        val query = build(rawSearch = "meeting tag:work notes", pinnedFirst = false)

        assertEquals(
            listOf("deleted NOT_EQUAL_TO true", "tags LIKE work", "null MATCH meeting* notes*"),
            conditionsOf(query)
        )
    }

    @Test
    fun bareTagPrefixAddsEmptyLikeAndNoFullTextMatch() {
        val query = build(rawSearch = "tag:", pinnedFirst = false)

        assertEquals(listOf("deleted NOT_EQUAL_TO true", "tags LIKE "), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview"), includesOf(query))
    }

    @Test
    fun tagOnlySearchOmitsFullTextMatch() {
        val query = build(rawSearch = "tag:work", pinnedFirst = false)

        assertEquals(listOf("deleted NOT_EQUAL_TO true", "tags LIKE work"), conditionsOf(query))
        assertEquals(listOf("title", "contentPreview"), includesOf(query))
    }

    @Test
    fun tagLikeValueHasNoWildcards() {
        val query = build(rawSearch = "tag:work", pinnedFirst = false)

        val subject = query.conditions.last().subject as String
        assertEquals("work", subject)
        assertFalse(subject.contains("%"))
    }

    @Test
    fun tagPrefixIsCaseSensitive() {
        val query = build(rawSearch = "TAG:work", pinnedFirst = false)

        assertEquals(listOf("deleted NOT_EQUAL_TO true", fullTextMatch("TAG:work")), conditionsOf(query))
    }

    // Sort clauses, as PrefUtils.sortNoteQuery emits them.

    private val expectedSorters = mapOf(
        SortOrder.MODIFIED_DESC to "modified DESCENDING",
        SortOrder.MODIFIED_ASC to "modified ASCENDING",
        SortOrder.CREATED_DESC to "created DESCENDING",
        SortOrder.CREATED_ASC to "created ASCENDING",
        SortOrder.CONTENT_ASC to "content ASCENDING",
        SortOrder.CONTENT_DESC to "content DESCENDING",
    )

    @Test
    fun sorterForEachSortOrder() {
        for (sort in SortOrder.values()) {
            val query = build(sort = sort, pinnedFirst = false)

            assertEquals(sort.name, listOf(expectedSorters.getValue(sort)), sortersOf(query))
        }
    }

    @Test
    fun pinnedOrderingPrecedesEverySortOrder() {
        for (sort in SortOrder.values()) {
            val query = build(sort = sort, pinnedFirst = true)

            assertEquals(sort.name, listOf("pinned DESCENDING", expectedSorters.getValue(sort)), sortersOf(query))
        }
    }
}
