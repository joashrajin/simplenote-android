package com.automattic.simplenote.repositories

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * Read surface over the notes bucket. Each method reproduces an existing query shape:
 * [search] the note list queries behind SearchQueryBuilder, [getNote] the bucket lookups that
 * treat a missing object as absence, [trashedNoteCount] NotesActivity.updateTrashMenuItem,
 * [interlinkSuggestions] the NoteEditorFragment link autocomplete, [referencesTo]
 * Note.getReferences as InfoBottomSheetDialog consumes it, and [hasUnsyncedNotes] plus
 * [allNotesForExport] the PreferencesFragment logout and export scans.
 *
 * The write surface reproduces the legacy mutation sequences: [createNote] the shared-content
 * and welcome-note paths (the NoteListFragment FAB path sets more fields before its single
 * save and needs an extended surface at wiring time), [setTrashed] NotesActivity.trashNote/onUndo
 * and TrashNotesTask,
 * [setPinned] NoteUtils.setNotePin, [emptyTrash] EmptyTrashTask, [setPreviewEnabled] and
 * [setPublished] the editor toggles, [getRevisions] the history sheet request, and
 * [noteChanges] the bucket listeners NotesActivity registers.
 */
interface NotesRepository {
    suspend fun search(request: NoteSearchRequest): NoteQueryResult
    suspend fun getNote(key: String): Note?
    suspend fun trashedNoteCount(): Int
    suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult
    suspend fun referencesTo(key: String): List<NoteReference>
    suspend fun hasUnsyncedNotes(): Boolean
    suspend fun allNotesForExport(): List<Note>

    // A non-null key can throw BucketObjectNameInvalid; the caller owns key validity.
    suspend fun createNote(content: String = "", key: String? = null): Note
    suspend fun saveNote(note: Note)
    suspend fun setTrashed(keys: List<String>, trashed: Boolean)
    suspend fun emptyTrash()
    suspend fun setPinned(keys: List<String>, pinned: Boolean)
    suspend fun setPreviewEnabled(key: String, enabled: Boolean)
    suspend fun setPublished(key: String, published: Boolean)
    suspend fun getRevisions(key: String, max: Int): RevisionsResult
    fun noteChanges(): Flow<NoteChange>
}

sealed class NoteChange {
    data class Saved(val key: String) : NoteChange()
    data class Deleted(val key: String) : NoteChange()
    data class NetworkChanged(val type: Bucket.ChangeType, val key: String?) : NoteChange()
}

sealed class RevisionsResult {
    data class Success(val revisions: List<Note>) : RevisionsResult()
    object Failure : RevisionsResult()
}

sealed class NoteQueryResult {
    // The cursor is live and filled on the IO dispatcher; ownership transfers to the caller,
    // who must close it.
    data class Notes(val cursor: Bucket.ObjectCursor<Note>, val searchSnapshot: String?) : NoteQueryResult()
    object InvalidQuery : NoteQueryResult()
}

data class NoteReference(
    val key: String,
    val title: String,
    val date: Calendar,
    val count: Int,
)
