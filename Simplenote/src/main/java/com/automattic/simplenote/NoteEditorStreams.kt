package com.automattic.simplenote

import android.app.Activity
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteChange
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.RevisionsResult
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The coroutine seam between the Java editor classes — NoteEditorFragment, NoteEditorActivity,
 * NoteUtils, SimplenoteLinkify — and [NotesRepository]. It carries the editor's note load/save
 * data access that used to run on AsyncTasks or synchronously on Main: [loadNote] replaces
 * LoadNoteTask's bucket read (the repository owns its missing-key fallback scan), [saveNote]
 * SaveNoteTask's background execution of the fragment's synchronous save, and the mutation
 * methods the direct get-mutate-save sequences in NoteEditorActivity and NoteUtils.
 *
 * Writes run inside [NonCancellable] because every legacy counterpart — an AsyncTask already
 * started, or a synchronous call on Main — always ran to completion once begun. Callbacks run
 * on Main and are skipped once the scope dies, like the legacy post steps behind their
 * activity-gone guards.
 */
class NoteEditorStreams @JvmOverloads constructor(
    private val notesRepository: NotesRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var changeCollection: Job? = null

    fun loadNote(key: String, onLoaded: Consumer<Note?>) {
        scope.launch {
            val note = notesRepository.getNote(key)
            if (isActive) {
                onLoaded.accept(note)
            }
        }
    }

    /** [save] is NoteEditorFragment.saveNote, still synchronous and bucket-based this phase. */
    fun saveNote(save: Runnable, onSaved: Runnable) {
        scope.launch {
            withContext(NonCancellable + ioDispatcher) {
                save.run()
            }
            if (isActive) {
                onSaved.run()
            }
        }
    }

    /**
     * The re-read after the write renews the caller's note reference, the way the legacy code
     * observed the mutation through the shared instance it had fetched itself; null on a
     * missing key, which legacy surfaced as a caught exception that left the reference alone.
     */
    fun setPreviewEnabled(key: String, enabled: Boolean, onComplete: Consumer<Note?>) {
        scope.launch {
            val refreshed = withContext(NonCancellable) {
                notesRepository.setPreviewEnabled(key, enabled)
                notesRepository.getNote(key)
            }
            if (isActive) {
                onComplete.accept(refreshed)
            }
        }
    }

    fun setPinned(key: String, pinned: Boolean, onComplete: Consumer<Note?>) {
        scope.launch {
            val refreshed = withContext(NonCancellable) {
                notesRepository.setPinned(listOf(key), pinned)
                notesRepository.getNote(key)
            }
            if (isActive) {
                onComplete.accept(refreshed)
            }
        }
    }

    fun setPublished(key: String, published: Boolean, onComplete: Consumer<Note?>) {
        scope.launch {
            val refreshed = withContext(NonCancellable) {
                notesRepository.setPublished(key, published)
                notesRepository.getNote(key)
            }
            if (isActive) {
                onComplete.accept(refreshed)
            }
        }
    }

    /**
     * The history sheet's revisions request. Delivery on Main replaces the sheet's own
     * runOnUiThread marshalling around the raw Simperium callbacks; [RevisionsResult.Failure]
     * carries both the request error and a vanished key. Cancellable: the sheet the result
     * feeds dies with the scope.
     */
    fun getRevisions(key: String, max: Int, onResult: Consumer<RevisionsResult>) {
        scope.launch {
            val result = notesRepository.getRevisions(key, max)
            if (isActive) {
                onResult.accept(result)
            }
        }
    }

    fun referencesTo(key: String, onReferences: Consumer<List<NoteReference>>) {
        scope.launch {
            val references = notesRepository.referencesTo(key)
            if (isActive) {
                onReferences.accept(references)
            }
        }
    }

    /**
     * Blocking bridge for the editor's link-autocomplete CursorAdapter, whose
     * runQueryOnBackgroundThread contract is a synchronous return on the filter's background
     * thread — the one caller that cannot take a Main callback. Never call on Main.
     */
    fun interlinkSuggestions(titleFilter: String, sort: SortOrder): NoteQueryResult = runBlocking {
        notesRepository.interlinkSuggestions(titleFilter, sort)
    }

    /**
     * The NoteEditorFragment side of the [noteChanges][NotesRepository.noteChanges] collection
     * that replaces its data bucket-listener callbacks. All methods run on Main.
     */
    interface ChangeListener {
        /** The key the editor holds open right now, or null before a note loads. */
        fun currentNoteKey(): String?

        /** The legacy onNetworkChange MODIFY branch for the open note, with the re-read done. */
        fun onNoteModifiedRemotely(refreshed: Note)

        /** Every save callback in the bucket, any key, re-read for the fragment's sync log. */
        fun onNoteSaved(note: Note)
    }

    /**
     * Starts the change collection unless it is already running: the legacy counterpart was a
     * listener re-add on every onResume that the bucket's Set deduplicated. The fragment owns
     * the window — resume to (destroy | save-while-paused self-removal) — which is why this is
     * an explicit start/stop pair and not repeatOnLifecycle(RESUMED): the legacy listener kept
     * applying remote modifies to a paused editor, and the self-removal point is an event, not
     * a lifecycle state.
     */
    fun startChangeCollection(listener: ChangeListener) {
        if (changeCollection?.isActive == true) {
            return
        }
        changeCollection = scope.launch {
            collectChanges(listener)
        }
    }

    fun stopChangeCollection() {
        changeCollection?.cancel()
        changeCollection = null
    }

    /**
     * One change stream pump, factored out of [startChangeCollection] for the unit tests (the
     * NotesActivityStreams precedent). A MODIFY for the open note re-reads it and swallows a
     * missing key like the legacy BucketObjectMissingException catch; a save re-reads the saved
     * instance the legacy callback carried, and a vanished one delivers nothing; deletes were an
     * empty legacy callback.
     */
    suspend fun collectChanges(listener: ChangeListener) {
        notesRepository.noteChanges().collect { change ->
            when (change) {
                is NoteChange.NetworkChanged -> {
                    val key = change.key
                    if (change.type == Bucket.ChangeType.MODIFY && key != null && key == listener.currentNoteKey()) {
                        val refreshed = notesRepository.getNote(key)
                        if (refreshed != null) {
                            listener.onNoteModifiedRemotely(refreshed)
                        }
                    }
                }
                is NoteChange.Saved -> {
                    val note = notesRepository.getNote(change.key)
                    if (note != null) {
                        listener.onNoteSaved(note)
                    }
                }
                is NoteChange.Deleted -> Unit
            }
        }
    }

    fun setTrashed(key: String, trashed: Boolean) {
        scope.launch {
            withContext(NonCancellable) {
                notesRepository.setTrashed(listOf(key), trashed)
            }
        }
    }

    fun permanentlyDelete(key: String, onDeleted: Runnable) {
        scope.launch {
            withContext(NonCancellable) {
                notesRepository.permanentlyDelete(key)
            }
            if (isActive) {
                onDeleted.run()
            }
        }
    }

    companion object {
        /**
         * Builds the seam for the static utility entry points (NoteUtils, SimplenoteLinkify)
         * that cannot take constructor injection. The activity must be a [LifecycleOwner];
         * every activity hosting these utilities is.
         */
        @JvmStatic
        fun from(activity: Activity): NoteEditorStreams {
            val repository = EntryPointAccessors.fromApplication(
                activity.applicationContext,
                NotesRepositoryEntryPoint::class.java,
            ).notesRepository()
            return NoteEditorStreams(repository, (activity as LifecycleOwner).lifecycleScope)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotesRepositoryEntryPoint {
    fun notesRepository(): NotesRepository
}
