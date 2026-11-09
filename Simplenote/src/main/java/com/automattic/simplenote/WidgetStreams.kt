package com.automattic.simplenote

import android.content.BroadcastReceiver
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.automattic.simplenote.utils.PrefUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The coroutine seam between the Java widget stack — the four AppWidgetProviders,
 * NoteListWidgetFactory, and the two configure activities — and [NotesRepository]. Providers
 * and the factory take no constructor injection, so they reach the repository through
 * [NotesRepositoryEntryPoint] at each use (now a singleton over the singleton notes bucket,
 * so each use resolves the same instance). Providers also have no lifecycle
 * or DI for a dispatcher, so [runAsync] builds its scope on [Dispatchers.IO] directly.
 */
object WidgetStreams {

    @JvmStatic
    fun notesRepository(context: Context): NotesRepository = EntryPointAccessors.fromApplication(
        context.applicationContext,
        NotesRepositoryEntryPoint::class.java,
    ).notesRepository()

    /**
     * Runs a provider's widget update off Main behind [BroadcastReceiver.goAsync]. The legacy
     * providers queried the bucket synchronously inside onUpdate on the main thread.
     */
    @JvmStatic
    fun runAsync(pendingResult: BroadcastReceiver.PendingResult, update: Runnable) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                update.run()
            } finally {
                // A PendingResult left unfinished on any path keeps the broadcast alive until
                // the system times it out and ANRs the receiver.
                pendingResult.finish()
            }
        }
    }

    /** Blocking bridge for callers already off Main: the [runAsync] job. Never call on Main. */
    @JvmStatic
    fun getNoteBlocking(repository: NotesRepository, key: String): Note? = runBlocking {
        repository.getNote(key)
    }

    /**
     * Blocking bridge for the [runAsync] job and for NoteListWidgetFactory.onDataSetChanged,
     * which RemoteViewsService delivers on a binder thread. Never call on Main.
     */
    @JvmStatic
    fun searchBlocking(repository: NotesRepository, request: NoteSearchRequest): NoteQueryResult = runBlocking {
        repository.search(request)
    }

    /** The configure dialogs' list query, delivered on Main within the activity's lifecycle. */
    @JvmStatic
    fun search(
        owner: LifecycleOwner,
        repository: NotesRepository,
        request: NoteSearchRequest,
        onResult: Consumer<NoteQueryResult>,
    ) {
        owner.lifecycleScope.launch {
            onResult.accept(repository.search(request))
        }
    }

    /**
     * The one query the whole widget stack ran: Note.all with title and preview included, plus
     * PrefUtils.sortNoteQuery's pinned-first ordering and preference sort. SearchQueryBuilder's
     * pinnedFirst also includes the pinned index in the projection, which the legacy widget
     * query left out; no widget consumer reads cursor columns it did not already have.
     */
    @JvmStatic
    fun widgetListRequest(context: Context): NoteSearchRequest =
        widgetListRequest(PrefUtils.getIntPref(context, PrefUtils.PREF_SORT_ORDER))

    @JvmStatic
    @VisibleForTesting
    fun widgetListRequest(sortPreference: Int): NoteSearchRequest = NoteSearchRequest(
        filter = NoteFilter.AllNotes,
        rawSearch = null,
        sort = SortOrder.fromPreference(sortPreference),
        pinnedFirst = true,
    )
}
