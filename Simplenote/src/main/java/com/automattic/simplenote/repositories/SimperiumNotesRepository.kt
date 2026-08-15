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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject
import kotlin.coroutines.resume

class SimperiumNotesRepository @Inject constructor(
    private val notesBucket: Bucket<Note>,
    private val searchQueryBuilder: SearchQueryBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NotesRepository {

    override suspend fun search(request: NoteSearchRequest): NoteQueryResult {
        var cursor: Bucket.ObjectCursor<Note>? = null
        return try {
            val result = withContext(ioDispatcher) {
                val queryCursor = searchQueryBuilder.build(notesBucket, request).execute()
                cursor = queryCursor
                // The cursor fills lazily, so an invalid Query.FullTextMatch term surfaces on the first count.
                queryCursor.count
                ensureActive()
                NoteQueryResult.Notes(queryCursor, request.rawSearch)
            }
            cursor = null
            result
        } catch (exception: SQLiteException) {
            closeCursorAfterFailure(cursor, exception)
            Log.e(Simplenote.TAG, "Invalid SQL statement", exception)
            currentCoroutineContext().ensureActive()
            NoteQueryResult.InvalidQuery
        } catch (exception: Throwable) {
            closeCursorAfterFailure(cursor, exception)
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

    override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult {
        var cursor: Bucket.ObjectCursor<Note>? = null
        return try {
            val result = withContext(ioDispatcher) {
                val query = notesBucket.query()
                query.include(Note.PINNED_INDEX_NAME)
                query.include(Note.TITLE_INDEX_NAME)
                query.where(Note.DELETED_PROPERTY, Query.ComparisonType.NOT_EQUAL_TO, true)
                query.where(Note.TITLE_INDEX_NAME, Query.ComparisonType.LIKE, "%$titleFilter%")
                query.order(Note.PINNED_INDEX_NAME, Query.SortType.DESCENDING)
                applySortOrder(query, sort)
                val queryCursor = query.execute()
                cursor = queryCursor
                // Fill on the IO dispatcher, matching the legacy background filter thread.
                queryCursor.count
                ensureActive()
                NoteQueryResult.Notes(queryCursor, null)
            }
            cursor = null
            result
        } catch (exception: Throwable) {
            closeCursorAfterFailure(cursor, exception)
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

    override suspend fun createNote(content: String, key: String?): Note = withContext(ioDispatcher) {
        val note = if (key == null) notesBucket.newObject() else notesBucket.newObject(key)
        note.creationDate = Calendar.getInstance()
        note.modificationDate = note.creationDate
        note.setContent(content)
        if (key != null) {
            // The welcome-note path primes the lazy title before the first save.
            note.title
        }
        note.save()
        note
    }

    override suspend fun saveNote(note: Note) = withContext(ioDispatcher) {
        note.save()
    }

    override suspend fun setTrashed(keys: List<String>, trashed: Boolean) = withContext(ioDispatcher) {
        for (key in keys) {
            val note = getOrSkip(key) ?: continue
            if (note.isDeleted == trashed) {
                continue
            }
            note.isDeleted = trashed
            note.modificationDate = Calendar.getInstance()
            note.save()
        }
    }

    override suspend fun emptyTrash() = withContext(ioDispatcher) {
        Note.allDeleted(notesBucket).execute().use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getObject().delete()
            }
        }
    }

    override suspend fun setPinned(keys: List<String>, pinned: Boolean) = withContext(ioDispatcher) {
        for (key in keys) {
            val note = getOrSkip(key) ?: continue
            if (note.isPinned == pinned) {
                continue
            }
            note.isPinned = pinned
            note.modificationDate = Calendar.getInstance()
            note.save()
        }
    }

    override suspend fun setPreviewEnabled(key: String, enabled: Boolean) = withContext(ioDispatcher) {
        val note = getOrSkip(key) ?: return@withContext
        note.isPreviewEnabled = enabled
        note.save()
    }

    override suspend fun setPublished(key: String, published: Boolean) = withContext(ioDispatcher) {
        val note = getOrSkip(key) ?: return@withContext
        note.isPublished = published
        note.save()
    }

    override suspend fun getRevisions(key: String, max: Int): RevisionsResult = withContext(ioDispatcher) {
        val note = getOrSkip(key) ?: return@withContext RevisionsResult.Failure
        suspendCancellableCoroutine<RevisionsResult> { continuation ->
            notesBucket.getRevisions(note, max, object : Bucket.RevisionsRequestCallbacks<Note> {
                override fun onComplete(revisionsMap: MutableMap<Int, Note>) {
                    if (continuation.isActive) {
                        continuation.resume(RevisionsResult.Success(revisionsMap.values.toList()))
                    }
                }

                override fun onRevision(key: String, version: Int, payload: JSONObject) = Unit

                override fun onError(exception: Throwable) {
                    if (continuation.isActive) {
                        continuation.resume(RevisionsResult.Failure)
                    }
                }
            })
        }
    }

    override fun noteChanges(): Flow<NoteChange> = callbackFlow {
        val networkListener = Bucket.OnNetworkChangeListener<Note> { _, type, key ->
            trySend(NoteChange.NetworkChanged(type, key))
        }
        val saveListener = Bucket.OnSaveObjectListener<Note> { _, note ->
            trySend(NoteChange.Saved(note.simperiumKey))
        }
        val deleteListener = Bucket.OnDeleteObjectListener<Note> { _, note ->
            trySend(NoteChange.Deleted(note.simperiumKey))
        }
        notesBucket.addOnNetworkChangeListener(networkListener)
        notesBucket.addOnSaveObjectListener(saveListener)
        notesBucket.addOnDeleteObjectListener(deleteListener)
        awaitClose {
            notesBucket.removeOnNetworkChangeListener(networkListener)
            notesBucket.removeOnSaveObjectListener(saveListener)
            notesBucket.removeOnDeleteObjectListener(deleteListener)
        }
        // Keyed events must not be dropped under backpressure: legacy consumers act per
        // callback, so the stream buffers losslessly instead of conflating.
    }.buffer(Channel.UNLIMITED).flowOn(ioDispatcher)

    private fun getOrSkip(key: String): Note? = try {
        notesBucket.get(key)
    } catch (exception: BucketObjectMissingException) {
        null
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

    private fun closeCursorAfterFailure(cursor: Bucket.ObjectCursor<Note>?, failure: Throwable) {
        try {
            cursor?.close()
        } catch (closeFailure: Throwable) {
            if (closeFailure !== failure) {
                failure.addSuppressed(closeFailure)
            }
        }
    }

    private fun referenceCount(key: String, content: String): Int =
        Regex(SIMPLENOTE_LINK_PREFIX + key).findAll(content).count()
}
