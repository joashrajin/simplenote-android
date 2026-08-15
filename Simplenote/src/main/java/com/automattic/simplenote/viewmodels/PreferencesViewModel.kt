package com.automattic.simplenote.viewmodels

import android.net.Uri
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.usecases.ExportNotesUseCase
import com.automattic.simplenote.utils.AppLog
import com.automattic.simplenote.utils.ExportNotesGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val exportNotesUseCase: ExportNotesUseCase,
    private val exportNotesGate: ExportNotesGate,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val logoutDecisionChannel = Channel<LogoutDecision>(Channel.CONFLATED)
    val logoutDecisions: Flow<LogoutDecision> = logoutDecisionChannel.receiveAsFlow()
    private val exportResultChannel = Channel<ExportResult>(Channel.BUFFERED)
    val exportResults: Flow<ExportResult> = exportResultChannel.receiveAsFlow()
    private var logoutCheckJob: Job? = null
    private val exportGeneration = exportNotesGate.currentGeneration()

    fun checkLogoutSafety() {
        if (logoutCheckJob?.isActive == true) {
            return
        }

        logoutCheckJob = viewModelScope.launch {
            val decision = try {
                if (notesRepository.hasUnsyncedNotes()) {
                    LogoutDecision.WARN_UNSYNCED
                } else {
                    LogoutDecision.PROCEED
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                AppLog.add(AppLog.Type.ACTION, "Blocked logout after the unsynced-note check failed")
                LogoutDecision.WARN_UNSYNCED
            }

            if (isActive) {
                logoutDecisionChannel.send(decision)
            }
        }
    }

    fun exportNotes(uri: Uri, unsyncedOnly: Boolean) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = withContext(NonCancellable + ioDispatcher) {
                try {
                    exportNotesGate.runExport {
                        exportNotesUseCase(uri, unsyncedOnly, exportGeneration)
                    }
                    ExportResult.SUCCESS
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    AppLog.add(AppLog.Type.ACTION, "Note export failed")
                    ExportResult.FAILURE
                }
            }

            if (isActive) {
                exportResultChannel.send(result)
            }
        }
    }
}

fun observeLogoutDecisions(
    decisions: Flow<LogoutDecision>,
    owner: LifecycleOwner,
    observer: Consumer<LogoutDecision>,
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            decisions.collect { decision -> observer.accept(decision) }
        }
    }
}

fun observeExportResults(
    results: Flow<ExportResult>,
    owner: LifecycleOwner,
    observer: Consumer<ExportResult>,
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            results.collect { result -> observer.accept(result) }
        }
    }
}

enum class LogoutDecision {
    WARN_UNSYNCED,
    PROCEED,
}

enum class ExportResult {
    SUCCESS,
    FAILURE,
}
