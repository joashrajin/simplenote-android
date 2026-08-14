package com.automattic.simplenote.search

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.utils.SearchTokenizer
import com.simperium.client.Bucket
import com.simperium.client.Query
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Builds the note list and search queries exactly as NoteListFragment.queryNotes,
 * NoteListFragment.queryNotesForSearch, and PrefUtils.sortNoteQuery construct them today.
 * Not yet wired into production; characterized by SearchQueryBuilderTest.
 */
class SearchQueryBuilder @Inject constructor() {

    fun build(bucket: Bucket<Note>, request: NoteSearchRequest): Query<Note> {
        val query = when (val filter = request.filter) {
            is NoteFilter.AllNotes -> Note.all(bucket)
            is NoteFilter.Trash -> Note.allDeleted(bucket)
            is NoteFilter.Untagged -> Note.allWithNoTag(bucket)
            is NoteFilter.InTag -> Note.allInTag(bucket, filter.tagName)
        }

        val searchString = extractTagConditions(query, request.rawSearch)
        if (!searchString.isNullOrEmpty()) {
            query.where(Query.FullTextMatch(SearchTokenizer(searchString)))
            query.include(Query.FullTextOffsets(MATCH_OFFSETS_INDEX_NAME))
            query.include(Query.FullTextSnippet(Note.MATCHED_TITLE_INDEX_NAME, Note.TITLE_INDEX_NAME))
            query.include(Query.FullTextSnippet(Note.MATCHED_CONTENT_INDEX_NAME, Note.CONTENT_PROPERTY))
            query.include(Note.TITLE_INDEX_NAME, Note.CONTENT_PREVIEW_INDEX_NAME)
        } else {
            query.include(Note.TITLE_INDEX_NAME, Note.CONTENT_PREVIEW_INDEX_NAME)
        }

        if (request.pinnedFirst) {
            query.include(Note.PINNED_INDEX_NAME)
            query.order(Note.PINNED_INDEX_NAME, Query.SortType.DESCENDING)
        }

        when (request.sort) {
            SortOrder.MODIFIED_DESC -> query.order(Note.MODIFIED_INDEX_NAME, Query.SortType.DESCENDING)
            SortOrder.MODIFIED_ASC -> query.order(Note.MODIFIED_INDEX_NAME, Query.SortType.ASCENDING)
            SortOrder.CREATED_DESC -> query.order(Note.CREATED_INDEX_NAME, Query.SortType.DESCENDING)
            SortOrder.CREATED_ASC -> query.order(Note.CREATED_INDEX_NAME, Query.SortType.ASCENDING)
            SortOrder.CONTENT_ASC -> query.order(Note.CONTENT_PROPERTY, Query.SortType.ASCENDING)
            SortOrder.CONTENT_DESC -> query.order(Note.CONTENT_PROPERTY, Query.SortType.DESCENDING)
        }

        return query
    }

    private fun extractTagConditions(query: Query<Note>, rawSearch: String?): String? {
        if (rawSearch.isNullOrEmpty()) {
            return rawSearch
        }
        val matcher = TAG_PATTERN.matcher(rawSearch)
        while (matcher.find()) {
            query.where(Note.TAGS_PROPERTY, Query.ComparisonType.LIKE, matcher.group(1))
        }
        return matcher.replaceAll("")
    }

    companion object {
        const val TAG_PREFIX = "tag:"
        private const val MATCH_OFFSETS_INDEX_NAME = "match_offsets"
        private val TAG_PATTERN = Pattern.compile("$TAG_PREFIX(.*?)( |\$)")
    }
}
