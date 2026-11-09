package com.automattic.simplenote

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import java.util.Calendar
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

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

    @Test
    fun setPublishedWritesThenHandsBackTheReReadNote() = runTest {
        val refreshed = Note("a")
        repository.notes["a"] = refreshed
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.setPublished("a", true) { note -> received.add(note) }
        runCurrent()

        assertEquals(listOf("setPublished(a, true)"), repository.operations)
        assertEquals(listOf("a"), repository.lookups)
        assertEquals(listOf<Note?>(refreshed), received)
    }

    @Test
    fun getRevisionsDeliversTheRepositorySuccess() = runTest {
        val revision = Note("a")
        repository.revisionsResult = RevisionsResult.Success(listOf(revision))
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<RevisionsResult>()

        streams.getRevisions("a", 30) { result -> received.add(result) }
        runCurrent()

        assertEquals(listOf("getRevisions(a, 30)"), repository.operations)
        assertEquals(listOf<RevisionsResult>(RevisionsResult.Success(listOf(revision))), received)
    }

    @Test
    fun getRevisionsDeliversTheRepositoryFailure() = runTest {
        repository.revisionsResult = RevisionsResult.Failure
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<RevisionsResult>()

        streams.getRevisions("a", 30) { result -> received.add(result) }
        runCurrent()

        assertEquals(listOf<RevisionsResult>(RevisionsResult.Failure), received)
    }

    @Test
    fun referencesToDeliversTheRepositoryReferences() = runTest {
        val reference = NoteReference("b", "Referencing note", Calendar.getInstance(), 2)
        repository.references = listOf(reference)
        val streams = NoteEditorStreams(repository, backgroundScope)
        val received = mutableListOf<List<NoteReference>>()

        streams.referencesTo("a") { references -> received.add(references) }
        runCurrent()

        assertEquals(listOf("referencesTo(a)"), repository.operations)
        assertEquals(listOf(listOf(reference)), received)
    }

    @Test
    fun interlinkSuggestionsBlocksThroughToTheRepositoryResult() {
        // No runTest: the method's contract is a synchronous return on the caller's thread,
        // the way the autocomplete CursorAdapter consumes it on its filter thread.
        val cursor = mock<Bucket.ObjectCursor<Note>>()
        repository.interlinkResult = NoteQueryResult.Notes(cursor, null)
        val streams = NoteEditorStreams(repository, CoroutineScope(SupervisorJob()))

        val result = streams.interlinkSuggestions("groc", SortOrder.MODIFIED_DESC)

        assertSame(repository.interlinkResult, result)
        assertEquals(listOf("interlinkSuggestions(groc, MODIFIED_DESC)"), repository.operations)
    }

    @Test
    fun interlinkSuggestionsPassesAnInvalidQueryThrough() {
        repository.interlinkResult = NoteQueryResult.InvalidQuery
        val streams = NoteEditorStreams(repository, CoroutineScope(SupervisorJob()))

        assertSame(NoteQueryResult.InvalidQuery, streams.interlinkSuggestions("%", SortOrder.CONTENT_ASC))
    }

    @Test
    fun aRemoteModifyForTheCurrentKeyDeliversTheRefreshedNote() = runTest {
        repository.notes["a"] = Note("a")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener().apply { currentKey = "a" }

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.MODIFY, "a"))
        runCurrent()

        assertEquals(listOf("modified:a"), listener.events)
        assertEquals(listOf("a"), repository.lookups)
    }

    @Test
    fun aRemoteModifyForAnotherKeyIsIgnored() = runTest {
        repository.notes["b"] = Note("b")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener().apply { currentKey = "a" }

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.MODIFY, "b"))
        runCurrent()

        assertTrue(listener.events.isEmpty())
        assertTrue(repository.lookups.isEmpty())
    }

    @Test
    fun aRemoteModifyNeverMatchesWhenNoNoteIsOpen() = runTest {
        // Legacy compared through the held note and a null key could never match; the keyless
        // flow must not let null == null pair a keyless change with a note-less editor.
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.MODIFY, null))
        runCurrent()

        assertTrue(listener.events.isEmpty())
        assertTrue(repository.lookups.isEmpty())
    }

    @Test
    fun aRemoteModifyWhoseNoteVanishedIsSwallowed() = runTest {
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener().apply { currentKey = "a" }

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.MODIFY, "a"))
        runCurrent()

        assertEquals(listOf("a"), repository.lookups)
        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun nonModifyNetworkChangesAreIgnored() = runTest {
        repository.notes["a"] = Note("a")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener().apply { currentKey = "a" }

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.INSERT, "a"))
        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.INDEX, null))
        runCurrent()

        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun aSavedChangeDeliversTheReReadNote() = runTest {
        repository.notes["a"] = Note("a")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.Saved("a"))
        runCurrent()

        assertEquals(listOf("saved:a"), listener.events)
        assertEquals(listOf("a"), repository.lookups)
    }

    @Test
    fun aSavedChangeForAVanishedNoteDeliversNothing() = runTest {
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.Saved("gone"))
        runCurrent()

        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun deletedChangesAreIgnored() = runTest {
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.Deleted("a"))
        runCurrent()

        assertTrue(listener.events.isEmpty())
        assertTrue(repository.lookups.isEmpty())
    }

    @Test
    fun aSavedCallbackCanTearDownTheCollectionLikeTheSaveWhilePausedRemoval() = runTest {
        repository.notes["a"] = Note("a")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()
        listener.onSaved = { streams.stopChangeCollection() }

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.Saved("a"))
        runCurrent()
        repository.changes.emit(NoteChange.Saved("a"))
        runCurrent()

        assertEquals(listOf("saved:a"), listener.events)
    }

    @Test
    fun statementsAfterAStopInsideTheSavedCallbackStillRun() = runTest {
        // The fragment's save-while-paused teardown removes its before-update listener and
        // writes an AppLog line AFTER calling stopChangeCollection from inside the callback;
        // the cancel must be cooperative enough for those statements to execute.
        repository.notes["a"] = Note("a")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()
        listener.onSaved = {
            streams.stopChangeCollection()
            listener.events.add("after-stop")
        }

        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.Saved("a"))
        runCurrent()

        assertEquals(listOf("saved:a", "after-stop"), listener.events)
    }

    @Test
    fun startChangeCollectionIsIdempotentWhileRunning() = runTest {
        // The legacy counterpart was an addListener on every onResume that the bucket's
        // listener Set deduplicated; a second start must not double-deliver.
        repository.notes["a"] = Note("a")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()

        streams.startChangeCollection(listener)
        runCurrent()
        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.Saved("a"))
        runCurrent()

        assertEquals(listOf("saved:a"), listener.events)
    }

    @Test
    fun theChangeCollectionRestartsAfterAStop() = runTest {
        repository.notes["a"] = Note("a")
        val streams = NoteEditorStreams(repository, backgroundScope)
        val listener = RecordingChangeListener()

        streams.startChangeCollection(listener)
        runCurrent()
        streams.stopChangeCollection()
        runCurrent()
        streams.startChangeCollection(listener)
        runCurrent()
        repository.changes.emit(NoteChange.Saved("a"))
        runCurrent()

        assertEquals(listOf("saved:a"), listener.events)
    }

    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatched = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched = true
            delegate.dispatch(context, block)
        }
    }

    private class RecordingChangeListener : NoteEditorStreams.ChangeListener {
        var currentKey: String? = null
        var onSaved: (Note) -> Unit = {}
        val events = mutableListOf<String>()

        override fun currentNoteKey(): String? = currentKey

        override fun onNoteModifiedRemotely(refreshed: Note) {
            events.add("modified:" + refreshed.simperiumKey)
        }

        override fun onNoteSaved(note: Note) {
            events.add("saved:" + note.simperiumKey)
            onSaved(note)
        }
    }

    private class FakeNotesRepository : NotesRepository {
        val changes = MutableSharedFlow<NoteChange>()
        val notes = mutableMapOf<String, Note>()
        val lookups = mutableListOf<String>()
        val operations = mutableListOf<String>()
        var onOperation: (String) -> Unit = {}
        var getNoteGate: CompletableDeferred<Unit>? = null
        var revisionsResult: RevisionsResult = RevisionsResult.Failure
        var references: List<NoteReference> = emptyList()
        var interlinkResult: NoteQueryResult = NoteQueryResult.InvalidQuery

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

        override suspend fun setPublished(key: String, published: Boolean) {
            record("setPublished($key, $published)")
        }

        override suspend fun setTrashed(keys: List<String>, trashed: Boolean) {
            record("setTrashed($keys, $trashed)")
        }

        override suspend fun permanentlyDelete(key: String) {
            record("permanentlyDelete($key)")
        }

        override suspend fun getRevisions(key: String, max: Int): RevisionsResult {
            record("getRevisions($key, $max)")
            return revisionsResult
        }

        override suspend fun referencesTo(key: String): List<NoteReference> {
            record("referencesTo($key)")
            return references
        }

        override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult {
            record("interlinkSuggestions($titleFilter, $sort)")
            return interlinkResult
        }

        override fun noteChanges(): Flow<NoteChange> = changes

        override suspend fun search(request: NoteSearchRequest): NoteQueryResult =
            error("unused in these tests")

        override suspend fun createNote(content: String, key: String?): Note =
            error("unused in these tests")

        override suspend fun saveNote(note: Note) = error("unused in these tests")

        override suspend fun emptyTrash() = error("unused in these tests")

        override suspend fun trashedNoteCount(): Int = error("unused in these tests")

        override suspend fun hasUnsyncedNotes(): Boolean = error("unused in these tests")

        override suspend fun allNotesForExport(): List<Note> = error("unused in these tests")
    }
}
