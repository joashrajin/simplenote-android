package com.automattic.simplenote.viewmodels

import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.automattic.simplenote.usecases.LoadNoteWidgetPickerUseCase
import com.automattic.simplenote.usecases.WidgetNotePickerItem
import com.automattic.simplenote.usecases.WidgetNotePickerSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteWidgetPickerViewModel @Inject constructor(
    private val loadNoteWidgetPicker: LoadNoteWidgetPickerUseCase,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<NoteWidgetPickerUiState>(NoteWidgetPickerUiState.Idle)
    val uiState: StateFlow<NoteWidgetPickerUiState> = mutableUiState.asStateFlow()

    private val mutableSelectionState =
        MutableStateFlow<NoteWidgetPickerSelectionState>(NoteWidgetPickerSelectionState.Idle)
    val selectionState: StateFlow<NoteWidgetPickerSelectionState> = mutableSelectionState.asStateFlow()

    private var loadStarted = false
    private var selectionJob: Job? = null
    private var nextSelectionRequestId = 0L

    fun load() {
        if (loadStarted) {
            return
        }

        loadStarted = true
        mutableUiState.value = NoteWidgetPickerUiState.Loading
        viewModelScope.launch {
            try {
                val items = loadNoteWidgetPicker.loadItems()
                if (isActive) {
                    mutableUiState.value = NoteWidgetPickerUiState.Content(items)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (isActive) {
                    mutableUiState.value = NoteWidgetPickerUiState.Failed(exception)
                }
            }
        }
    }

    fun selectNote(key: String) {
        if (selectionJob?.isActive == true || mutableSelectionState.value !is NoteWidgetPickerSelectionState.Idle) {
            return
        }

        val requestId = ++nextSelectionRequestId
        mutableSelectionState.value = NoteWidgetPickerSelectionState.Loading(requestId, key)
        selectionJob = viewModelScope.launch {
            try {
                val selection = loadNoteWidgetPicker.loadSelection(key)
                if (isActive && isCurrentSelection(requestId)) {
                    mutableSelectionState.value = if (selection == null) {
                        NoteWidgetPickerSelectionState.Missing(requestId, key)
                    } else {
                        NoteWidgetPickerSelectionState.Selected(requestId, selection)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (isActive && isCurrentSelection(requestId)) {
                    mutableSelectionState.value = NoteWidgetPickerSelectionState.Failed(requestId, key, exception)
                }
            }
        }
    }

    fun consumeSelection(requestId: Long) {
        val state = mutableSelectionState.value
        if (state.requestIdOrNull() == requestId && state !is NoteWidgetPickerSelectionState.Loading) {
            mutableSelectionState.value = NoteWidgetPickerSelectionState.Idle
        }
    }

    private fun isCurrentSelection(requestId: Long): Boolean =
        mutableSelectionState.value.requestIdOrNull() == requestId
}

fun observeNoteWidgetPickerUiState(
    states: Flow<NoteWidgetPickerUiState>,
    owner: LifecycleOwner,
    observer: Consumer<NoteWidgetPickerUiState>,
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            states.collect(observer::accept)
        }
    }
}

fun observeNoteWidgetPickerSelectionState(
    states: Flow<NoteWidgetPickerSelectionState>,
    owner: LifecycleOwner,
    observer: Consumer<NoteWidgetPickerSelectionState>,
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            states.collect(observer::accept)
        }
    }
}

sealed class NoteWidgetPickerUiState {
    object Idle : NoteWidgetPickerUiState()
    object Loading : NoteWidgetPickerUiState()
    data class Content(val items: List<WidgetNotePickerItem>) : NoteWidgetPickerUiState()
    data class Failed(val cause: Throwable) : NoteWidgetPickerUiState()
}

sealed class NoteWidgetPickerSelectionState {
    object Idle : NoteWidgetPickerSelectionState()
    data class Loading(val requestId: Long, val key: String) : NoteWidgetPickerSelectionState()
    data class Selected(
        val requestId: Long,
        val selection: WidgetNotePickerSelection,
    ) : NoteWidgetPickerSelectionState()
    data class Missing(val requestId: Long, val key: String) : NoteWidgetPickerSelectionState()
    data class Failed(val requestId: Long, val key: String, val cause: Throwable) : NoteWidgetPickerSelectionState()
}

private fun NoteWidgetPickerSelectionState.requestIdOrNull(): Long? = when (this) {
    NoteWidgetPickerSelectionState.Idle -> null
    is NoteWidgetPickerSelectionState.Loading -> requestId
    is NoteWidgetPickerSelectionState.Selected -> requestId
    is NoteWidgetPickerSelectionState.Missing -> requestId
    is NoteWidgetPickerSelectionState.Failed -> requestId
}
