package com.automattic.simplenote.repositories

import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import kotlinx.coroutines.flow.Flow

interface TagsRepository {
    fun saveTag(tagName: String): Boolean
    fun isTagValid(tagName: String): Boolean
    fun isTagMissing(tagName: String): Boolean
    fun isTagConflict(tagName: String, oldTagName: String): Boolean
    fun getCanonicalTagName(tagName: String): String
    fun renameTag(tagName: String, oldTag: Tag): Boolean
    suspend fun allTags(): List<TagItem>
    suspend fun searchTags(query: String): List<TagItem>

    /**
     * Emits an initial navigation-drawer snapshot in manual or alphabetical order, followed by
     * refreshed snapshots when the underlying tags change.
     */
    fun navigationTags(sortAlphabetically: Boolean): Flow<List<Tag>>

    /**
     * Tag names for the search-suggestion overlay, exactly as NoteListFragment.getTagSuggestions
     * queried them: every tag ordered by note count (most used first); a query ending in the
     * literal tag: prefix suppresses the name filter, any other query is matched with a
     * contains-LIKE on the raw text. Not [searchTags] — that orders byKey and pays for note
     * counts the overlay never shows.
     */
    suspend fun suggestTags(query: String): List<String>
    suspend fun deleteTag(tag: Tag)
    suspend fun tagsChanged(): Flow<Boolean>
}
