package com.automattic.simplenote

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AsyncTask-replacement semantics NoteEditorStreams carries for the editor: loads deliver on
 * the scope and are dropped with it, writes survive the scope the way a started AsyncTask always
 * ran to completion, and the write-then-re-read mutations hand the caller a refreshed note.
 */
@ExperimentalCoroutinesApi
class NoteEditorStreamsTest {

    private val repository = FakeNotesRepository()

    @Test
    fun loadNoteDeliversTheRepositoryNote() = runTest {
        val note = Note("a")
        repository.notes["a"] = note
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.loadNote("a") { loaded -> received.add(loaded) }
        runCurrent()

        assertEquals(listOf<Note?>(note), received)
        assertEquals(listOf("a"), repository.lookups)
    }

    @Test
    fun loadNoteDeliversNullForAMissingKey() = runTest {
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.loadNote("gone") { loaded -> received.add(loaded) }
        runCurrent()

        assertEquals(listOf<Note?>(null), received)
    }

    @Test
    fun aLoadInFlightWhenTheScopeDiesNeverDelivers() = runTest {
        repository.notes["a"] = Note("a")
        repository.getNoteGate = CompletableDeferred()
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.loadNote("a") { loaded -> received.add(loaded) }
        runCurrent()
        backgroundScope.coroutineContext[Job]?.cancel()
        repository.getNoteGate?.complete(Unit)
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun saveRunsOnTheIoDispatcherAndCompletesBeforeTheCallback() = runTest {
        val ioDispatcher = RecordingDispatcher(StandardTestDispatcher(testScheduler))
        val streams = NoteEditorStreams(repository, backgroundScope, ioDispatcher)
        val order = mutableListOf<String>()

        streams.saveNote({ order.add("saved") }, { order.add("callback") })
        runCurrent()

        assertEquals(listOf("saved", "callback"), order)
        assertTrue(ioDispatcher.dispatched)
    }

    @Test
    fun aSaveAlreadyStartedSurvivesCancellationButSkipsTheCallback() = runTest {
        // The editor scope dying between a save's start and its completion is the
        // destroy-mid-autosave window; the legacy SaveNoteTask always ran to completion and
        // only its onPostExecute was guarded out.
        val scopeDispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + scopeDispatcher)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val streams = NoteEditorStreams(repository, scope, ioDispatcher)
        var saved = false
        var callbackRan = false

        streams.saveNote({ saved = true }, { callbackRan = true })
        scope.cancel()
        runCurrent()

        assertTrue(saved)
        assertFalse(callbackRan)
    }

    @Test
    fun setPreviewEnabledWritesThenHandsBackTheReReadNote() = runTest {
        val refreshed = Note("a")
        repository.notes["a"] = refreshed
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.setPreviewEnabled("a", true) { note -> received.add(note) }
        runCurrent()

        assertEquals(listOf("setPreviewEnabled(a, true)"), repository.operations)
        assertEquals(listOf("a"), repository.lookups)
        assertEquals(listOf<Note?>(refreshed), received)
    }

    @Test
    fun setPinnedWritesThenHandsBackTheReReadNote() = runTest {
        val refreshed = Note("a")
        repository.notes["a"] = refreshed
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.setPinned("a", true) { note -> received.add(note) }
        runCurrent()

        assertEquals(listOf("setPinned([a], true)"), repository.operations)
        assertEquals(listOf<Note?>(refreshed), received)
    }

    @Test
    fun aMutationOnAVanishedKeyHandsBackNull() = runTest {
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.setPinned("gone", true) { note -> received.add(note) }
        runCurrent()

        assertEquals(listOf("setPinned([gone], true)"), repository.operations)
        assertEquals(listOf<Note?>(null), received)
    }

    @Test
    fun setTrashedWritesThroughTheRepository() = runTest {
        val streams = NoteEditorStreams(repository, backgroundScope)

        streams.setTrashed("a", true)
        runCurrent()

        assertEquals(listOf("setTrashed([a], true)"), repository.operations)
    }

    @Test
    fun permanentlyDeleteRunsTheCallbackAfterTheRepositoryDelete() = runTest {
        val streams = NoteEditorStreams(repository, backgroundScope)
        val order = mutableListOf<String>()
        repository.onOperation = { order.add(it) }

        streams.permanentlyDelete("a") { order.add("deleted-callback") }
        runCurrent()

        assertEquals(listOf("permanentlyDelete(a)", "deleted-callback"), order)
    }

    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatched = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched = true
            delegate.dispatch(context, block)
        }
    }

    private class FakeNotesRepository : NotesRepository {
        val notes = mutableMapOf<String, Note>()
        val lookups = mutableListOf<String>()
        val operations = mutableListOf<String>()
        var onOperation: (String) -> Unit = {}
        var getNoteGate: CompletableDeferred<Unit>? = null

        private fun record(operation: String) {
            operations.add(operation)
            onOperation(operation)
        }

        override suspend fun getNote(key: String): Note? {
            lookups.add(key)
            getNoteGate?.await()
            return notes[key]
        }

        override suspend fun setPinned(keys: List<String>, pinned: Boolean) {
            record("setPinned($keys, $pinned)")
        }

        override suspend fun setPreviewEnabled(key: String, enabled: Boolean) {
            record("setPreviewEnabled($key, $enabled)")
        }

        override suspend fun setTrashed(keys: List<String>, trashed: Boolean) {
            record("setTrashed($keys, $trashed)")
        }

        override suspend fun permanentlyDelete(key: String) {
            record("permanentlyDelete($key)")
        }

        override fun noteChanges(): Flow<NoteChange> = error("unused in these tests")

        override suspend fun search(request: NoteSearchRequest): NoteQueryResult =
            error("unused in these tests")

        override suspend fun createNote(content: String, key: String?): Note =
            error("unused in these tests")

        override suspend fun saveNote(note: Note) = error("unused in these tests")

        override suspend fun emptyTrash() = error("unused in these tests")

        override suspend fun trashedNoteCount(): Int = error("unused in these tests")

        override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult =
            error("unused in these tests")

        override suspend fun referencesTo(key: String): List<NoteReference> =
            error("unused in these tests")

        override suspend fun hasUnsyncedNotes(): Boolean = error("unused in these tests")

        override suspend fun allNotesForExport(): List<Note> = error("unused in these tests")

        override suspend fun setPublished(key: String, published: Boolean) =
            error("unused in these tests")

        override suspend fun getRevisions(key: String, max: Int): RevisionsResult =
            error("unused in these tests")
    }
}
