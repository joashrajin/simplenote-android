package com.automattic.simplenote.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automattic.simplenote.models.Suggestion
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the note list refresh path that NoteListFragment.RefreshListTask, PinNotesTask,
 * TrashNotesTask, and RefreshListForSearchTask used to run on AsyncTask executors, plus the
 * search state ([searchString]/[isSearching]) and the suggestion feeds the fragment used to
 * compute inline. The fragment still renders: it swaps the delivered cursor into its
 * CursorAdapter (closing the previous one, exactly as changeCursor always has), keeps snippet
 * rendering keyed to the delivered search snapshot, and runs the legacy post-refresh callback
 * chain.
 */
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val preferencesRepository: PreferencesRepository,
    private val tagsRepository: TagsRepository,
) : ViewModel() {

    private val _noteList = MutableLiveData<NoteListUpdate>()
    val noteList: LiveData<NoteListUpdate> = _noteList

    private val _suggestions = MutableLiveData<SuggestionsUpdate>()
    val suggestions: LiveData<SuggestionsUpdate> = _suggestions

    var filter: NoteFilter = NoteFilter.AllNotes
        private set

    /** The live query text, exactly as the legacy NoteListFragment.mSearchString tracked it. */
    var searchString: String? = null
        private set

    /** Search mode, exactly as the legacy NoteListFragment.mIsSearching tracked it. */
    var isSearching: Boolean = false
        private set

    private var refreshJob: Job? = null
    private var refreshSequence = 0L
    private var suggestionsJob: Job? = null
    private val recentSearchMutex = Mutex()
    private var removedRecentSearchIndex = 0

    init {
        // The fragment used to register three raw preference-bucket listeners whose only job
        // was re-reading the recent searches after a save, delete, or remote change — that is
        // how a deleted recent search left the list and an undone one came back. The repository
        // change stream replaces them.
        viewModelScope.launch {
            preferencesRepository.preferencesChanged().collect {
                loadRecentSuggestions()
            }
        }
    }

    /**
     * Replaces RefreshListTask. Reproduces queryNotes: the selected filter, the live search
     * string (tag: extraction and the FTS include block live in SearchQueryBuilder), pinned
     * ordering first, then the preferred sort.
     */
    fun refreshList(filter: NoteFilter, fromNavSelect: Boolean) {
        this.filter = filter
        launchRefresh(filter, pinnedFirst = true, fromNavSelect = fromNavSelect)
    }

    /**
     * Replaces RefreshListForSearchTask. Reproduces queryNotesForSearch: an active search spans
     * every non-trashed note no matter which tag is selected, and drops the pinned-first
     * ordering. Riding the same sequenced pipeline as [refreshList] resolves what the two
     * legacy task chains left to a completion race (neither chain cancelled the other, so the
     * last onPostExecute won the adapter): the newest request now always wins.
     */
    fun refreshListForSearch() {
        launchRefresh(NoteFilter.AllNotes, pinnedFirst = false, fromNavSelect = false)
    }

    /**
     * A newer refresh of either kind always wins. The in-flight job is cancelled (the
     * repository closes its cursor when it observes the cancellation) and the sequence check
     * discards-and-closes any superseded result that slips past cancellation, so a stale
     * cursor can never be delivered after a newer one. Everything runs on Main; the repository
     * is main-safe.
     */
    private fun launchRefresh(filter: NoteFilter, pinnedFirst: Boolean, fromNavSelect: Boolean) {
        val search = searchString
        val sequence = ++refreshSequence
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val sort = preferencesRepository.sortOrder()
            val result = notesRepository.search(NoteSearchRequest(filter, search, sort, pinnedFirst))
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
     * The legacy searchNotes state machine: entering search mode, tracking the query, feeding
     * the suggestion overlay (recent searches on an empty query, tag suggestions otherwise),
     * and refreshing the list only on submit.
     */
    fun searchNotes(searchString: String, isSubmit: Boolean) {
        isSearching = true
        this.searchString = searchString

        if (searchString.isEmpty()) {
            loadRecentSuggestions()
        } else {
            loadTagSuggestions(searchString)
        }

        if (isSubmit) {
            refreshListForSearch()
        }
    }

    /** First half of the legacy clearSearch: leave search mode before the tag-filtered refresh. */
    fun stopSearching() {
        isSearching = false
    }

    /** Second half of the legacy clearSearch: drop the query before the final refresh. */
    fun clearSearchQuery() {
        searchString = null
    }

    fun hasSearchQuery(): Boolean = !searchString.isNullOrEmpty()

    /** Replaces the fragment's addSearchItem; the submit path passes index 0, undo the captured one. */
    fun addRecentSearch(query: String, index: Int) {
        viewModelScope.launch {
            // The legacy path ran these read-modify-writes synchronously on Main; the mutex
            // restores that serialization across the IO hops.
            recentSearchMutex.withLock {
                preferencesRepository.addRecentSearch(query, index)
            }
        }
    }

    /**
     * Replaces deleteSearchItem: the index the query occupied is captured (the legacy
     * mDeletedItemIndex) so the undo bar can put it back where it was.
     */
    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            recentSearchMutex.withLock {
                removedRecentSearchIndex = preferencesRepository.removeRecentSearch(query)
            }
        }
    }

    /** The undo action of the delete-recent-search snackbar. */
    fun restoreRemovedRecentSearch(query: String) {
        viewModelScope.launch {
            // Waits on the mutex so an instant undo reads the index its own delete captured.
            recentSearchMutex.withLock {
                preferencesRepository.addRecentSearch(query, removedRecentSearchIndex)
            }
        }
    }

    // Replaces getSearchItems; a newer suggestion load always supersedes the in-flight one,
    // matching the call order the legacy synchronous main-thread queries preserved trivially.
    private fun loadRecentSuggestions() {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            val recents = preferencesRepository.recentSearches()
            _suggestions.value = SuggestionsUpdate(
                recents.map { recent -> Suggestion(recent, Suggestion.Type.HISTORY) },
                isRecentSearches = true,
            )
        }
    }

    // Replaces getTagSuggestions: the raw query first, then the tag names by descending use.
    private fun loadTagSuggestions(query: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            val suggestions = mutableListOf(Suggestion(query, Suggestion.Type.QUERY))
            for (tagName in tagsRepository.suggestTags(query)) {
                suggestions.add(Suggestion(tagName, Suggestion.Type.TAG))
            }
            _suggestions.value = SuggestionsUpdate(suggestions, isRecentSearches = false)
        }
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

/**
 * One suggestion-overlay feed. [isRecentSearches] keeps the two legacy application styles
 * apart: getSearchItems diffed recents into the live adapter, while getTagSuggestions
 * installed a fresh adapter.
 */
class SuggestionsUpdate(val suggestions: List<Suggestion>, val isRecentSearches: Boolean)
