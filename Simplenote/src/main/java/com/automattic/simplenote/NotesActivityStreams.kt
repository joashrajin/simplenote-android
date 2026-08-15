package com.automattic.simplenote

import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.utils.AppLog
import com.automattic.simplenote.utils.AppLog.Type
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectNameInvalid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The coroutine seam between NotesActivity (Java) and [NotesRepository]. It owns the two things
 * the activity cannot express cleanly across the interop boundary: the [noteChanges][NotesRepository.noteChanges]
 * collection that replaces the activity's three raw notes-bucket listener callbacks, and the
 * suspend data operations (welcome/share note creation, trash, undo-restore, preview selection,
 * trash count, empty trash) that replace the direct bucket mutations and EmptyTrashTask.
 *
 * The legacy stopListeningToNotesBucket suppression hack — NoteListFragment.addNote removed the
 * activity's bucket listeners so the fresh note would not flash into the list, and the next
 * onResume re-added them — survives as the [mute] flag: muted changes are dropped on arrival,
 * never replayed, and [unmute] sits at the exact onResume point where the legacy re-add lived.
 * That placement also reproduces the legacy quirk that an addNote issued from within onResume
 * (the note-list-widget button path) was suppressed only until the re-add later in the same
 * onResume pass.
 *
 * Phase 2 lifts this wholesale: the listener methods become NotesViewModel state emissions and
 * the operations become its intents.
 */
class NotesActivityStreams(
    private val notesRepository: NotesRepository,
    private val scope: CoroutineScope,
) {
    interface Listener {
        /** The legacy onNetworkChange INDEX branch: initial indexing finished, hide the toolbar progress. */
        fun onIndexingComplete()

        /** Every un-muted change, any type: the legacy runOnUiThread { refreshList() } bodies. */
        fun onNotesChanged()

        /**
         * A note was saved and re-read from the bucket, always on Main. Carries the fresh
         * instance the legacy onSaveObject received as a callback argument; the activity keys
         * its current-note compare, menu invalidation, and sync log off it.
         */
        fun onNoteSaved(note: Note)
    }

    private var muted = false

    /**
     * Drop every note change until [unmute]. The legacy hack really removed the bucket
     * listeners, so dropped events were lost for good — no replay here either.
     */
    fun mute() {
        muted = true
        AppLog.add(Type.SYNC, "Muted note bucket listener (NotesActivity)")
    }

    fun unmute() {
        muted = false
    }

    /**
     * Collect [NotesRepository.noteChanges] while [lifecycle] is at least STARTED. The bucket
     * listeners genuinely attach and detach at these boundaries (the flow adds them on
     * collection and removes them on cancellation), so the legacy add/remove sync log lines
     * keep telling the truth from here.
     *
     * Deliberately wider than the legacy resume/pause listener window: paused-but-started
     * activities now receive un-muted events (benign refresh work, and an INDEX completion
     * can no longer strand the toolbar spinner while paused).
     */
    fun start(lifecycle: Lifecycle, listener: Listener) {
        scope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLog.add(Type.SYNC, "Added note bucket listener (NotesActivity)")
                try {
                    collectChanges(listener)
                } finally {
                    AppLog.add(Type.SYNC, "Removed note bucket listener (NotesActivity)")
                }
            }
        }
    }

    /**
     * One change stream pump, factored out of [start] so the dispatch and mute semantics are
     * unit-testable without a lifecycle. Runs on the caller's dispatcher (Main in production).
     *
     * The mute check happens once, on arrival: a save whose re-read is already in flight when
     * [mute] lands still delivers, matching the legacy listener whose callback body always ran
     * to completion once invoked.
     */
    suspend fun collectChanges(listener: Listener) {
        notesRepository.noteChanges().collect { change ->
            if (muted) {
                return@collect
            }

            when (change) {
                is NoteChange.NetworkChanged -> {
                    if (change.type == Bucket.ChangeType.INDEX) {
                        listener.onIndexingComplete()
                    }
                    listener.onNotesChanged()
                }
                is NoteChange.Saved -> {
                    listener.onNotesChanged()
                    // The legacy callback held the saved instance; the keyed stream re-reads it.
                    val note = notesRepository.getNote(change.key)
                    if (note != null) {
                        listener.onNoteSaved(note)
                    }
                }
                is NoteChange.Deleted -> listener.onNotesChanged()
            }
        }
    }

    /**
     * The first-launch welcome note. NonCancellable because the legacy creation ran
     * synchronously in onCreate and therefore always completed; the name-invalid catch mirrors
     * the legacy swallow — the fixed key is known valid.
     */
    fun createWelcomeNote(content: String) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    notesRepository.createNote(content, WELCOME_NOTE_KEY)
                } catch (exception: BucketObjectNameInvalid) {
                    // this won't happen because welcome-android is a valid name
                }
            }
        }
    }

    /**
     * The share-intent note. The creation always completes (legacy ran it synchronously);
     * [onCreated] runs on Main and is skipped when the activity is already gone, exactly as
     * the legacy select-new-note state died with the instance.
     */
    fun createNoteFromShare(content: String, onCreated: Consumer<Note>) {
        scope.launch {
            val note = withContext(NonCancellable) {
                notesRepository.createNote(content)
            }
            if (isActive) {
                onCreated.accept(note)
            }
        }
    }

    /**
     * The trash/restore toggle behind the activity's trashNote. Hands back the re-read note so
     * the activity can refresh its current-note reference the way the legacy code observed the
     * mutation through the shared instance it saved.
     */
    fun trashNote(key: String, trashed: Boolean, onComplete: Consumer<Note?>) {
        scope.launch {
            val refreshed = withContext(NonCancellable) {
                notesRepository.setTrashed(listOf(key), trashed)
                notesRepository.getNote(key)
            }
            if (isActive) {
                onComplete.accept(refreshed)
            }
        }
    }

    /** The undo-bar restore: every key rides one setTrashed pass with per-key-skip semantics. */
    fun restoreNotes(keys: List<String>, onComplete: Runnable) {
        scope.launch {
            withContext(NonCancellable) {
                notesRepository.setTrashed(keys, false)
            }
            if (isActive) {
                onComplete.run()
            }
        }
    }

    /** Complete each accepted preview repository handoff in order after the owning activity is destroyed. */
    fun setPreviewEnabled(key: String, enabled: Boolean) {
        scope.launch {
            withContext(NonCancellable) {
                previewWriteMutex.withLock {
                    notesRepository.setPreviewEnabled(key, enabled)
                }
            }
        }
    }

    /** The empty-trash menu count read; cancellable, the menu it decorates dies with the activity. */
    fun trashedNoteCount(onCount: Consumer<Int>) {
        scope.launch {
            val count = notesRepository.trashedNoteCount()
            onCount.accept(count)
        }
    }

    /**
     * Replaces EmptyTrashTask. NonCancellable so the destructive pass always finishes once
     * started (the legacy executor never stopped mid-loop); [onComplete] is skipped after
     * cancellation like the legacy SoftReference gate skipped a collected activity.
     */
    fun emptyTrash(onComplete: Runnable) {
        scope.launch {
            withContext(NonCancellable) {
                notesRepository.emptyTrash()
            }
            if (isActive) {
                onComplete.run()
            }
        }
    }

    companion object {
        const val WELCOME_NOTE_KEY = "welcome-android"
        private val previewWriteMutex = Mutex()
    }
}
