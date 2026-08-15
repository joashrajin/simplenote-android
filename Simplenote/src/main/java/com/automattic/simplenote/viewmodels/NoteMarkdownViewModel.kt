package com.automattic.simplenote.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteMarkdownViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NoteMarkdownState>(NoteMarkdownState.Idle)
    val uiState: StateFlow<NoteMarkdownState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var requestSequence = 0L

    fun loadNote(noteKey: String) {
        val requestId = ++requestSequence
        loadJob?.cancel()
        _uiState.value = NoteMarkdownState.Loading(requestId, noteKey)
        loadJob = viewModelScope.launch {
            try {
                val note = notesRepository.getNote(noteKey)
                if (!isActive || requestId != requestSequence) {
                    return@launch
                }
                _uiState.value = if (note == null) {
                    NoteMarkdownState.Missing(requestId, noteKey)
                } else {
                    NoteMarkdownState.Loaded(requestId, noteKey, note)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (isActive && requestId == requestSequence) {
                    _uiState.value = NoteMarkdownState.Error(requestId, noteKey, exception)
                }
            }
        }
    }

    override fun onCleared() {
        requestSequence++
        loadJob?.cancel()
        super.onCleared()
    }
}

sealed class NoteMarkdownState {
    object Idle : NoteMarkdownState()
    data class Loading(val requestId: Long, val noteKey: String) : NoteMarkdownState()
    data class Loaded(val requestId: Long, val noteKey: String, val note: Note) : NoteMarkdownState()
    data class Missing(val requestId: Long, val noteKey: String) : NoteMarkdownState()
    data class Error(val requestId: Long, val noteKey: String, val cause: Exception) : NoteMarkdownState()
}
