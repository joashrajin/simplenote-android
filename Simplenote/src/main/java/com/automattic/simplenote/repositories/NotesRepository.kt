package com.automattic.simplenote.repositories

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import kotlinx.coroutines.flow.Flow
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Calendar

/**
 * Read surface over the notes bucket. Each method reproduces an existing query shape:
 * [search] the note list queries behind SearchQueryBuilder, [getNote] the bucket lookups that
 * treat a missing object as absence (with the LoadNoteTask fallback scan of the object store
 * before concluding absence), [trashedNoteCount] NotesActivity.updateTrashMenuItem,
 * [interlinkSuggestions] the NoteEditorFragment link autocomplete, [referencesTo]
 * Note.getReferences as InfoBottomSheetDialog consumes it, and [hasUnsyncedNotes] plus
 * [allNotesForExport] the PreferencesFragment logout and export scans.
 *
 * The write surface reproduces the legacy mutation sequences: [createNote] the shared-content
 * and welcome-note paths (the NoteListFragment FAB path sets more fields before its single
 * save and needs an extended surface at wiring time), [setTrashed] NotesActivity.trashNote/onUndo
 * and TrashNotesTask,
 * [setPinned] NoteUtils.setNotePin, [emptyTrash] EmptyTrashTask, [permanentlyDelete] the
 * NoteUtils.showDialogDeletePermanently single-note delete, [setPreviewEnabled] and
 * [setPublished] the editor toggles, [getRevisions] the history sheet request, and
 * [noteChanges] the bucket listeners NotesActivity registers.
 *
 * The import surface ([noteForImport], [saveImportedNote]) stays split and non-suspending so the
 * Importer can reproduce its legacy sequence on the caller's thread: every note of a JSON export
 * built before the first save, the tag fixups (whose removeTag carries its own save) in between,
 * then exactly one closing save per note.
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

    /** An unsaved note built exactly as Note.fromContent built the txt/md imports. */
    fun noteForImport(content: String): Note

    /** An unsaved note built exactly as Note.fromExportedJson built the JSON imports. */
    @Throws(JSONException::class, ParseException::class)
    fun noteForImport(exportedJson: JSONObject): Note

    fun saveImportedNote(note: Note)
    suspend fun setTrashed(keys: List<String>, trashed: Boolean)
    suspend fun emptyTrash()
    suspend fun setPinned(keys: List<String>, pinned: Boolean)
    suspend fun permanentlyDelete(key: String)
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
