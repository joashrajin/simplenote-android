package com.automattic.simplenote.screenshottest

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.repositories.CollaboratorsActionResult
import com.automattic.simplenote.repositories.CollaboratorsRepository
import com.automattic.simplenote.repositories.MagicLinkRepository
import com.automattic.simplenote.repositories.MagicLinkResponseResult
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic in-memory repositories backing the screenshot surfaces. They replace the
 * Simperium-bucket implementations so screenshot tests never touch buckets, the network, or the
 * real [com.automattic.simplenote.Simplenote] application class.
 */
class ScreenshotTagsRepository : TagsRepository {
    override fun saveTag(tagName: String): Boolean = true
    override fun isTagValid(tagName: String): Boolean = true
    override fun isTagMissing(tagName: String): Boolean = false
    override fun isTagConflict(tagName: String, oldTagName: String): Boolean = false
    override fun getCanonicalTagName(tagName: String): String = tagName
    override fun renameTag(tagName: String, oldTag: Tag): Boolean = true
    override suspend fun allTags(): List<TagItem> = ScreenshotHarness.SAMPLE_TAG_ITEMS
    override suspend fun searchTags(query: String): List<TagItem> =
        ScreenshotHarness.SAMPLE_TAG_ITEMS.filter { it.tag.name.contains(query, ignoreCase = true) }

    override fun navigationTags(sortAlphabetically: Boolean): Flow<List<Tag>> {
        val tags = ScreenshotHarness.SAMPLE_TAG_ITEMS.map { item -> item.tag }
        return flowOf(if (sortAlphabetically) tags.sortedBy { tag -> tag.name.lowercase() } else tags)
    }
    override suspend fun suggestTags(query: String): List<String> =
        ScreenshotHarness.SAMPLE_TAG_ITEMS.map { it.tag.name }

    override suspend fun deleteTag(tag: Tag) = Unit
    override suspend fun tagsChanged(): Flow<Boolean> = emptyFlow()
}

class ScreenshotCollaboratorsRepository : CollaboratorsRepository {
    override fun isValidCollaborator(collaborator: String): Boolean = false
    override suspend fun getCollaborators(noteId: String): CollaboratorsActionResult =
        CollaboratorsActionResult.CollaboratorsList(emptyList())

    override suspend fun addCollaborator(noteId: String, collaborator: String): CollaboratorsActionResult =
        CollaboratorsActionResult.CollaboratorsList(emptyList())

    override suspend fun removeCollaborator(noteId: String, collaborator: String): CollaboratorsActionResult =
        CollaboratorsActionResult.CollaboratorsList(emptyList())

    override suspend fun collaboratorsChanged(noteId: String): Flow<Boolean> = emptyFlow()
}

class ScreenshotNotesRepository : NotesRepository {
    override suspend fun search(request: NoteSearchRequest): NoteQueryResult = NoteQueryResult.InvalidQuery
    override suspend fun getNote(key: String): Note? = null
    override suspend fun trashedNoteCount(): Int = 0
    override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult =
        NoteQueryResult.InvalidQuery

    override suspend fun referencesTo(key: String): List<NoteReference> = emptyList()
    override suspend fun hasUnsyncedNotes(): Boolean = false
    override suspend fun allNotesForExport(): List<Note> = emptyList()
    override suspend fun createNote(content: String, key: String?): Note = Note(key ?: "screenshot-note")
    override suspend fun saveNote(note: Note) = Unit
    override suspend fun setTrashed(keys: List<String>, trashed: Boolean) = Unit
    override suspend fun emptyTrash() = Unit
    override suspend fun setPinned(keys: List<String>, pinned: Boolean) = Unit
    override suspend fun setPreviewEnabled(key: String, enabled: Boolean) = Unit
    override suspend fun setPublished(key: String, published: Boolean) = Unit
    override suspend fun getRevisions(key: String, max: Int): RevisionsResult = RevisionsResult.Failure
    override fun noteChanges(): Flow<NoteChange> = emptyFlow()
}

class ScreenshotPreferencesRepository : PreferencesRepository {
    override suspend fun isAnalyticsEnabled(): Boolean = false
    override suspend fun setAnalyticsEnabled(enabled: Boolean) = Unit
    override fun analyticsEnabledSnapshot(): Boolean = false
    override suspend fun recentSearches(): List<String> = emptyList()
    override suspend fun addRecentSearch(query: String, index: Int) = Unit
    override suspend fun removeRecentSearch(query: String): Int = -1
    override fun preferencesChanged(): Flow<Unit> = emptyFlow()
    override suspend fun sortOrder(): SortOrder = SortOrder.MODIFIED_DESC
}

class ScreenshotMagicLinkRepository : MagicLinkRepository {
    override suspend fun completeLogin(username: String, authCode: String): MagicLinkResponseResult =
        MagicLinkResponseResult.MagicLinkError(code = 0)

    override suspend fun requestLogin(username: String): MagicLinkResponseResult =
        MagicLinkResponseResult.MagicLinkError(code = 0)
}
