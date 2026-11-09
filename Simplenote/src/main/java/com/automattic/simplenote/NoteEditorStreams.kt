package com.automattic.simplenote

import android.app.Activity
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
