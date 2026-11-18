package com.automattic.simplenote

import androidx.arch.core.util.Function
import androidx.core.util.Consumer
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * is lifecycle-bound. Both fail closed: a failed check reports unsynced notes so the user still gets
 * the warning, and a failed export reports failure through the toast.
 */
object PreferencesStreams {

    /** Returns [inFlight] untouched while it is still active, so a repeated logout tap runs one check. */
    @JvmStatic
    fun hasUnsyncedNotes(
        scope: CoroutineScope,
        repository: NotesRepository,
        inFlight: Job?,
        onResult: Consumer<Boolean>,
    ): Job {
        if (inFlight?.isActive == true) {
            return inFlight
        }
        return scope.launch {
            val hasUnsyncedNotes = try {
                repository.hasUnsyncedNotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            onResult.accept(hasUnsyncedNotes)
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
                try {
                    write.apply(repository.allNotesForExport())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
            if (isActive) {
                onToast.accept(success)
            }
        }
    }
}
