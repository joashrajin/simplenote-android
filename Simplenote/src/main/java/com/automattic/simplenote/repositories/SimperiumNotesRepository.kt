package com.automattic.simplenote.repositories

import android.database.sqlite.SQLiteException
import android.util.Log
import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SearchQueryBuilder
import com.automattic.simplenote.search.SortOrder
import com.automattic.simplenote.utils.SimplenoteLinkify.SIMPLENOTE_LINK_PREFIX
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import com.simperium.client.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SimperiumNotesRepository @Inject constructor(
    private val notesBucket: Bucket<Note>,
    private val searchQueryBuilder: SearchQueryBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NotesRepository {

    override suspend fun search(request: NoteSearchRequest): NoteQueryResult = withContext(ioDispatcher) {
        var cursor: Bucket.ObjectCursor<Note>? = null
        try {
            cursor = searchQueryBuilder.build(notesBucket, request).execute()
            // The cursor fills lazily, so an invalid Query.FullTextMatch term surfaces on the first count.
            cursor.count
            ensureActive()
            NoteQueryResult.Notes(cursor, request.rawSearch)
        } catch (exception: SQLiteException) {
            Log.e(Simplenote.TAG, "Invalid SQL statement", exception)
            cursor?.close()
            NoteQueryResult.InvalidQuery
        } catch (exception: CancellationException) {
            cursor?.close()
            throw exception
        }
    }

    override suspend fun getNote(key: String): Note? = withContext(ioDispatcher) {
        try {
            notesBucket.get(key)
        } catch (exception: BucketObjectMissingException) {
            null
        }
    }

    override suspend fun trashedNoteCount(): Int = withContext(ioDispatcher) {
        Note.allDeleted(notesBucket).count()
    }

    override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult =
        withContext(ioDispatcher) {
            val query = notesBucket.query()
            query.include(Note.PINNED_INDEX_NAME)
            query.include(Note.TITLE_INDEX_NAME)
            query.where(Note.DELETED_PROPERTY, Query.ComparisonType.NOT_EQUAL_TO, true)
            query.where(Note.TITLE_INDEX_NAME, Query.ComparisonType.LIKE, "%$titleFilter%")
            query.order(Note.PINNED_INDEX_NAME, Query.SortType.DESCENDING)
            applySortOrder(query, sort)
            val cursor = query.execute()
            try {
                // Fill on the IO dispatcher, matching the legacy background filter thread.
                cursor.count
                ensureActive()
                NoteQueryResult.Notes(cursor, null)
            } catch (exception: CancellationException) {
                cursor.close()
                throw exception
            }
        }

    override suspend fun referencesTo(key: String): List<NoteReference> = withContext(ioDispatcher) {
        Note.search(notesBucket, SIMPLENOTE_LINK_PREFIX + key).execute().use { cursor ->
            val references = mutableListOf<NoteReference>()
            while (cursor.moveToNext()) {
                val note = cursor.getObject()
                references.add(
                    NoteReference(
                        key = note.simperiumKey,
                        title = note.title,
                        date = note.modificationDate,
                        count = referenceCount(key, note.content),
                    )
                )
            }
            references
        }
    }

    override suspend fun hasUnsyncedNotes(): Boolean = withContext(ioDispatcher) {
        notesBucket.allObjects().use { cursor ->
            while (cursor.moveToNext()) {
                val note = cursor.getObject()
                if (note.isNew || note.isModified) {
                    return@use true
                }
            }
            false
        }
    }

    override suspend fun allNotesForExport(): List<Note> = withContext(ioDispatcher) {
        notesBucket.allObjects().use { cursor ->
            val notes = mutableListOf<Note>()
            while (cursor.moveToNext()) {
                notes.add(cursor.getObject())
            }
            notes
        }
    }

    private fun applySortOrder(query: Query<Note>, sort: SortOrder) {
        when (sort) {
            SortOrder.MODIFIED_DESC -> query.order(Note.MODIFIED_INDEX_NAME, Query.SortType.DESCENDING)
            SortOrder.MODIFIED_ASC -> query.order(Note.MODIFIED_INDEX_NAME, Query.SortType.ASCENDING)
            SortOrder.CREATED_DESC -> query.order(Note.CREATED_INDEX_NAME, Query.SortType.DESCENDING)
            SortOrder.CREATED_ASC -> query.order(Note.CREATED_INDEX_NAME, Query.SortType.ASCENDING)
            SortOrder.CONTENT_ASC -> query.order(Note.CONTENT_PROPERTY, Query.SortType.ASCENDING)
            SortOrder.CONTENT_DESC -> query.order(Note.CONTENT_PROPERTY, Query.SortType.DESCENDING)
        }
    }

    private fun referenceCount(key: String, content: String): Int =
        Regex(SIMPLENOTE_LINK_PREFIX + key).findAll(content).count()
}
