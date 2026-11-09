package com.automattic.simplenote

import androidx.core.util.Consumer
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The coroutine seam between PreferencesFragment and [NotesRepository]: the logout unsynced-notes
 * check that LogOutTask ran on an AsyncTask, and the export scan that walked the bucket cursor
 * synchronously on Main. Callbacks deliver on Main inside the fragment's lifecycle scope, so a
 * destroyed fragment drops them the way the legacy WeakReference guards returned early.
 */
object PreferencesStreams {

    @JvmStatic
    fun hasUnsyncedNotes(scope: CoroutineScope, repository: NotesRepository, onResult: Consumer<Boolean>) {
        scope.launch {
            onResult.accept(repository.hasUnsyncedNotes())
        }
    }

    @JvmStatic
    fun allNotesForExport(scope: CoroutineScope, repository: NotesRepository, onNotes: Consumer<List<Note>>) {
        scope.launch {
            onNotes.accept(repository.allNotesForExport())
        }
    }
}
