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
import com.simperium.client.BucketObjectNameInvalid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The change-stream dispatch and mute semantics NotesActivityStreams carries for NotesActivity:
 * the legacy bucket-listener bodies (INDEX toolbar branch, per-save re-read, refresh on every
 * change) and the stopListeningToNotesBucket suppression window reproduced as the mute flag.
 */
@ExperimentalCoroutinesApi
class NotesActivityStreamsTest {

    // repeatOnLifecycle hops through Dispatchers.Main.immediate; the rule provides it.
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private val repository = FakeNotesRepository()
    private val recorded = mutableListOf<String>()
    private val listener = object : NotesActivityStreams.Listener {
        override fun onIndexingComplete() {
            recorded.add("indexing-complete")
        }

        override fun onNotesChanged() {
            recorded.add("changed")
        }

        override fun onNoteSaved(note: Note) {
            recorded.add("saved:" + note.simperiumKey)
        }
    }

    private fun TestScope.collectingStreams(): NotesActivityStreams {
        val streams = NotesActivityStreams(repository, backgroundScope)
        backgroundScope.launch { streams.collectChanges(listener) }
        runCurrent()
        return streams
    }

    @Test
    fun indexNetworkChangeSignalsIndexingCompleteBeforeTheRefresh() = runTest {
        collectingStreams()

        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.INDEX, null))
        runCurrent()

        assertEquals(listOf("indexing-complete", "changed"), recorded)
    }

    @Test
    fun nonIndexNetworkChangeOnlyRefreshes() = runTest {
        collectingStreams()

        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.MODIFY, "a"))
        runCurrent()

        assertEquals(listOf("changed"), recorded)
    }

    @Test
    fun savedChangeRefreshesThenDeliversTheReReadNote() = runTest {
        repository.notes["a"] = Note("a")
        collectingStreams()

        repository.changes.emit(NoteChange.Saved("a"))
        runCurrent()

        assertEquals(listOf("changed", "saved:a"), recorded)
        assertEquals(listOf("a"), repository.lookups)
    }

    @Test
    fun savedChangeForAVanishedNoteRefreshesWithoutTheSaveCallback() = runTest {
        collectingStreams()

        repository.changes.emit(NoteChange.Saved("gone"))
        runCurrent()

        assertEquals(listOf("changed"), recorded)
    }

    @Test
    fun deletedChangeOnlyRefreshes() = runTest {
        collectingStreams()

        repository.changes.emit(NoteChange.Deleted("a"))
        runCurrent()

        assertEquals(listOf("changed"), recorded)
    }

    @Test
    fun mutedChangesAreDroppedForGoodAndUnmuteResumesDelivery() = runTest {
        repository.notes["a"] = Note("a")
        val streams = collectingStreams()

        streams.mute()
        repository.changes.emit(NoteChange.NetworkChanged(Bucket.ChangeType.INDEX, null))
        repository.changes.emit(NoteChange.Saved("a"))
        repository.changes.emit(NoteChange.Deleted("a"))
        runCurrent()

        // The legacy hack removed the listeners outright: nothing is processed, nothing is
        // replayed, and the save is never even re-read.
        assertTrue(recorded.isEmpty())
        assertTrue(repository.lookups.isEmpty())

        streams.unmute()
        repository.changes.emit(NoteChange.Deleted("a"))
        runCurrent()

        assertEquals(listOf("changed"), recorded)
    }

    @Test
    fun aSaveReReadAlreadyInFlightWhenTheMuteLandsStillDelivers() = runTest {
        repository.notes["a"] = Note("a")
        repository.getNoteGate = CompletableDeferred()
        val streams = collectingStreams()

        backgroundScope.launch { repository.changes.emit(NoteChange.Saved("a")) }
        runCurrent()
        streams.mute()
        repository.getNoteGate?.complete(Unit)
        runCurrent()

        // The legacy listener body always ran to completion once invoked; the mute gates
        // arrival, not resolution.
        assertEquals(listOf("changed", "saved:a"), recorded)
    }

    @Test
    fun trashNoteTogglesThroughTheRepositoryAndHandsBackTheReReadNote() = runTest {
        val note = Note("a")
        repository.notes["a"] = note
        val streams = NotesActivityStreams(repository, backgroundScope)
        val received = mutableListOf<Note?>()

        streams.trashNote("a", true) { refreshed -> received.add(refreshed) }
        runCurrent()

        assertEquals(listOf("setTrashed([a], true)"), repository.operations)
        assertEquals(listOf<Note?>(note), received)
    }

    @Test
    fun restoreNotesRestoresEveryKeyBeforeTheCompletion() = runTest {
        val streams = NotesActivityStreams(repository, backgroundScope)
        val completions = mutableListOf<String>()
        repository.onOperation = { completions.add(it) }

        streams.restoreNotes(listOf("x", "y")) { completions.add("complete") }
        runCurrent()

        assertEquals(listOf("setTrashed([x, y], false)", "complete"), completions)
    }

    @Test
    fun emptyTrashRunsTheCompletionAfterTheRepositoryPass() = runTest {
        val streams = NotesActivityStreams(repository, backgroundScope)
        val completions = mutableListOf<String>()
        repository.onOperation = { completions.add(it) }

        streams.emptyTrash { completions.add("complete") }
        runCurrent()

        assertEquals(listOf("emptyTrash", "complete"), completions)
    }

    @Test
    fun theWelcomeNoteRidesTheFixedKey() = runTest {
        val streams = NotesActivityStreams(repository, backgroundScope)

        streams.createWelcomeNote("welcome")
        runCurrent()

        assertEquals(listOf("createNote(welcome, welcome-android)"), repository.operations)
    }

    @Test
    fun anInvalidWelcomeNoteNameIsSwallowed() = runTest {
        // The legacy creation caught BucketObjectNameInvalid and moved on; an escape here
        // would fail the test through the scope's uncaught-exception reporting.
        repository.createNoteFailure = BucketObjectNameInvalid("welcome-android")
        val streams = NotesActivityStreams(repository, backgroundScope)

        streams.createWelcomeNote("welcome")
        runCurrent()

        assertTrue(repository.operations.isEmpty())
    }

    @Test
    fun aSharedNoteIsCreatedWithoutAKeyAndDelivered() = runTest {
        val streams = NotesActivityStreams(repository, backgroundScope)
        val received = mutableListOf<Note>()

        streams.createNoteFromShare("shared text") { note -> received.add(note) }
        runCurrent()

        assertEquals(listOf("createNote(shared text, null)"), repository.operations)
        assertEquals(listOf("generated"), received.map { note -> note.simperiumKey })
    }

    @Test
    fun trashedNoteCountDeliversTheRepositoryCount() = runTest {
        repository.trashedCount = 3
        val streams = NotesActivityStreams(repository, backgroundScope)
        val counts = mutableListOf<Int>()

        streams.trashedNoteCount { count -> counts.add(count) }
        runCurrent()

        assertEquals(listOf(3), counts)
    }

    @Test
    fun startCollectsOnlyWhileStarted() = runTest {
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED, UnconfinedTestDispatcher(testScheduler))
        val streams = NotesActivityStreams(repository, backgroundScope)
        streams.start(owner.lifecycle, listener)
        runCurrent()

        backgroundScope.launch { repository.changes.emit(NoteChange.Deleted("early")) }
        runCurrent()
        assertEquals(emptyList<String>(), recorded)

        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        backgroundScope.launch { repository.changes.emit(NoteChange.Deleted("during")) }
        runCurrent()
        assertEquals(listOf("changed"), recorded)

        owner.currentState = Lifecycle.State.CREATED
        runCurrent()
        backgroundScope.launch { repository.changes.emit(NoteChange.Deleted("stopped")) }
        runCurrent()
        assertEquals(listOf("changed"), recorded)
    }

    @Test
    fun muteSurvivesTheStopStartCycle() = runTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(testScheduler))
        val streams = NotesActivityStreams(repository, backgroundScope)
        streams.start(owner.lifecycle, listener)
        runCurrent()
        streams.mute()

        owner.currentState = Lifecycle.State.CREATED
        runCurrent()
        owner.currentState = Lifecycle.State.STARTED
        runCurrent()

        backgroundScope.launch { repository.changes.emit(NoteChange.Deleted("muted")) }
        runCurrent()
        assertEquals(emptyList<String>(), recorded)

        streams.unmute()
        backgroundScope.launch { repository.changes.emit(NoteChange.Deleted("open")) }
        runCurrent()
        assertEquals(listOf("changed"), recorded)
    }

    private class FakeNotesRepository : NotesRepository {
        val changes = MutableSharedFlow<NoteChange>()
        val notes = mutableMapOf<String, Note>()
        val lookups = mutableListOf<String>()
        val operations = mutableListOf<String>()
        var onOperation: (String) -> Unit = {}
        var getNoteGate: CompletableDeferred<Unit>? = null
        var createNoteFailure: Exception? = null
        var trashedCount = 0

        private fun record(operation: String) {
            operations.add(operation)
            onOperation(operation)
        }

        override fun noteChanges(): Flow<NoteChange> = changes
        override fun observeNote(key: String): Flow<Note?> = flowOf(notes[key])

        override suspend fun getNote(key: String): Note? {
            lookups.add(key)
            getNoteGate?.await()
            return notes[key]
        }

        override suspend fun createNote(content: String, key: String?): Note {
            createNoteFailure?.let { failure -> throw failure }
            record("createNote($content, $key)")
            return Note(key ?: "generated")
        }

        override suspend fun setTrashed(keys: List<String>, trashed: Boolean) {
            record("setTrashed($keys, $trashed)")
        }

        override suspend fun emptyTrash() {
            record("emptyTrash")
        }

        override suspend fun trashedNoteCount(): Int = trashedCount

        override suspend fun search(request: NoteSearchRequest): NoteQueryResult =
            error("unused in these tests")

        override suspend fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult =
            error("unused in these tests")

        override suspend fun referencesTo(key: String): List<NoteReference> =
            error("unused in these tests")

        override suspend fun hasUnsyncedNotes(): Boolean = error("unused in these tests")

        override suspend fun allNotesForExport(): List<Note> = error("unused in these tests")

        override suspend fun saveNote(note: Note) = error("unused in these tests")

        override suspend fun setPinned(keys: List<String>, pinned: Boolean) =
            error("unused in these tests")

        override suspend fun setPreviewEnabled(key: String, enabled: Boolean) =
            error("unused in these tests")

        override suspend fun setPublished(key: String, published: Boolean) =
            error("unused in these tests")

        override suspend fun getRevisions(key: String, max: Int): RevisionsResult =
            error("unused in these tests")
    }
}
