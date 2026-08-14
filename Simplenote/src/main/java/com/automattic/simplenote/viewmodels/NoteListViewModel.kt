package com.automattic.simplenote.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the note list refresh path that NoteListFragment.RefreshListTask, PinNotesTask, and
 * TrashNotesTask used to run on AsyncTask executors. The fragment still renders: it swaps the
 * delivered cursor into its CursorAdapter (closing the previous one, exactly as changeCursor
 * always has) and runs the legacy post-refresh callback chain. The search path
 * (queryNotesForSearch/RefreshListForSearchTask) is deliberately untouched here.
 */
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _noteList = MutableLiveData<NoteListUpdate>()
    val noteList: LiveData<NoteListUpdate> = _noteList

    var filter: NoteFilter = NoteFilter.AllNotes
        private set

    private var refreshJob: Job? = null
    private var refreshSequence = 0L

    /**
     * Replaces RefreshListTask. Reproduces queryNotes: the selected filter, the live search
     * string (tag: extraction and the FTS include block live in SearchQueryBuilder), pinned
     * ordering first, then the preferred sort.
     *
     * A newer refresh always wins. The in-flight job is cancelled (the repository closes its
     * cursor when it observes the cancellation) and the sequence check discards-and-closes any
     * superseded result that slips past cancellation, so a stale cursor can never be delivered
     * after a newer one. Everything runs on Main; the repository is main-safe.
     */
    fun refreshList(filter: NoteFilter, searchString: String?, fromNavSelect: Boolean) {
        this.filter = filter
        val sequence = ++refreshSequence
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val sort = preferencesRepository.sortOrder()
            val result = notesRepository.search(
                NoteSearchRequest(filter, searchString, sort, pinnedFirst = true)
            )
            if (sequence != refreshSequence) {
                (result as? NoteQueryResult.Notes)?.cursor?.close()
                return@launch
            }
            deliver(NoteListUpdate(result, fromNavSelect))
        }
    }

    // A replaced update that never reached the adapter still owns its cursor.
    private fun deliver(update: NoteListUpdate) {
        val previous = _noteList.value
        if (previous != null && previous.sideEffectsPending) {
            (previous.result as? NoteQueryResult.Notes)?.cursor?.close()
        }
        _noteList.value = update
    }

    /**
     * Replaces PinNotesTask. The fragment partitions its checked rows by current pin state
     * (the legacy task toggled each note) and keeps the ActionMode teardown in [onComplete].
     */
    fun pinNotes(notesToPin: List<String>, notesToUnpin: List<String>, onComplete: Runnable) {
        viewModelScope.launch {
            // The legacy executor always finished its saves once started; keep that guarantee
            // even if the scope is cleared mid-flight.
            withContext(NonCancellable) {
                notesRepository.setPinned(notesToPin, true)
                notesRepository.setPinned(notesToUnpin, false)
            }
            if (isActive) {
                onComplete.run()
            }
        }
    }

    /**
     * Replaces TrashNotesTask. Same partitioning contract as [pinNotes]; the undo bar and
     * selection updates stay with the fragment in [onComplete].
     */
    fun trashNotes(notesToTrash: List<String>, notesToRestore: List<String>, onComplete: Runnable) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                notesRepository.setTrashed(notesToTrash, true)
                notesRepository.setTrashed(notesToRestore, false)
            }
            if (isActive) {
                onComplete.run()
            }
        }
    }

    override fun onCleared() {
        // The UI outlives every delivered cursor swap except the last: nothing is left to close
        // the adapter's final cursor once the activity is gone for good.
        (_noteList.value?.result as? NoteQueryResult.Notes)?.cursor?.close()
    }
}

/**
 * One refresh delivery. The cursor swap is idempotent, so sticky LiveData redelivery after a
 * view recreation may repeat it, but the legacy onPostExecute side effects (nav selection,
 * trash menu update, pending note selection) must run exactly once per refresh —
 * [consumeSideEffects] latches that.
 */
class NoteListUpdate(val result: NoteQueryResult, val isFromNavSelect: Boolean) {
    private val sideEffectsConsumed = AtomicBoolean(false)

    val sideEffectsPending: Boolean
        get() = !sideEffectsConsumed.get()

    fun consumeSideEffects(): Boolean = !sideEffectsConsumed.getAndSet(true)
}
