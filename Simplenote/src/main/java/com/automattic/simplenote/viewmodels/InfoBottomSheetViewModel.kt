package com.automattic.simplenote.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automattic.simplenote.repositories.NoteReference
import com.automattic.simplenote.repositories.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InfoBottomSheetViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
) : ViewModel() {
    private val _referenceState = MutableLiveData<ReferenceState>(ReferenceState.Idle)
    val referenceState: LiveData<ReferenceState> = _referenceState

    private var activeRequestId: Long? = null
    private var loadJob: Job? = null
    private var requestSequence = 0L

    fun loadReferences(noteKey: String): Long {
        val requestId = ++requestSequence
        activeRequestId = requestId
        loadJob?.cancel()
        _referenceState.value = ReferenceState.Loading(requestId, noteKey)

        loadJob = viewModelScope.launch {
            val state = try {
                ReferenceState.Loaded(requestId, noteKey, notesRepository.referencesTo(noteKey))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                ReferenceState.Error(requestId, noteKey)
            }

            if (isActive && requestId == activeRequestId) {
                _referenceState.value = state
            }
        }

        return requestId
    }

    fun cancelReferences(requestId: Long) {
        if (activeRequestId != requestId) {
            return
        }

        invalidateRequest()
        _referenceState.value = ReferenceState.Idle
    }

    override fun onCleared() {
        invalidateRequest()
        _referenceState.value = ReferenceState.Idle
        super.onCleared()
    }

    private fun invalidateRequest() {
        requestSequence++
        activeRequestId = null
        loadJob?.cancel()
        loadJob = null
    }
}

sealed class ReferenceState {
    object Idle : ReferenceState()
    data class Loading(val requestId: Long, val noteKey: String) : ReferenceState()
    data class Loaded(
        val requestId: Long,
        val noteKey: String,
        val references: List<NoteReference>,
    ) : ReferenceState()
    data class Error(val requestId: Long, val noteKey: String) : ReferenceState()
}
