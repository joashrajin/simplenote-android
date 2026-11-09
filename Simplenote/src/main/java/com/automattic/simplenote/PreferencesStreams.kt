package com.automattic.simplenote

import androidx.arch.core.util.Function
import androidx.core.util.Consumer
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The coroutine seam between PreferencesFragment and [NotesRepository]: the logout unsynced-notes
 * check that LogOutTask ran on an AsyncTask, and the export scan-and-write that ran synchronously
 * on Main. The unsynced-notes result dies with the scope the way the legacy WeakReference guard
 * dropped work for a dead fragment; the export's fetch and file write run under [NonCancellable]
 * because the legacy write could never be lost once the user had picked a file — only its toast
 * is lifecycle-bound.
 */
object PreferencesStreams {

    @JvmStatic
    fun hasUnsyncedNotes(scope: CoroutineScope, repository: NotesRepository, onResult: Consumer<Boolean>) {
        scope.launch {
            onResult.accept(repository.hasUnsyncedNotes())
        }
    }

    /** [write] runs off Main and must not touch fragment lifecycle; it returns write success. */
    @JvmStatic
    @JvmOverloads
    fun exportNotes(
        scope: CoroutineScope,
        repository: NotesRepository,
        write: Function<List<Note>, Boolean>,
        onToast: Consumer<Boolean>,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        scope.launch {
            val success = withContext(NonCancellable + ioDispatcher) {
                write.apply(repository.allNotesForExport())
            }
            if (isActive) {
                onToast.accept(success)
            }
        }
    }
}
