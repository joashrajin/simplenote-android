package com.automattic.simplenote.repositories

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import java.util.Calendar

/**
 * Read surface over the notes bucket. Each method reproduces an existing query shape:
 * [search] the note list queries behind SearchQueryBuilder, [getNote] the bucket lookups that
 * treat a missing object as absence, [trashedNoteCount] NotesActivity.updateTrashMenuItem,
 * [interlinkSuggestions] the NoteEditorFragment link autocomplete, [referencesTo]
 * Note.getReferences as InfoBottomSheetDialog consumes it, and [hasUnsyncedNotes] plus
 * [allNotesForExport] the PreferencesFragment logout and export scans.
 */
interface NotesRepository {
    suspend fun search(request: NoteSearchRequest): NoteQueryResult
    suspend fun getNote(key: String): Note?
    suspend fun trashedNoteCount(): Int
    suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult
    suspend fun referencesTo(key: String): List<NoteReference>
    suspend fun hasUnsyncedNotes(): Boolean
    suspend fun allNotesForExport(): List<Note>
}

sealed class NoteQueryResult {
    data class Notes(val cursor: Bucket.ObjectCursor<Note>, val searchSnapshot: String?) : NoteQueryResult()
    object InvalidQuery : NoteQueryResult()
}

data class NoteReference(
    val key: String,
    val title: String,
    val date: Calendar,
    val count: Int,
)
