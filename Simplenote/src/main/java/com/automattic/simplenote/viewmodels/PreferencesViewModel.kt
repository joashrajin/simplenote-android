package com.automattic.simplenote.viewmodels

import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.utils.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
) : ViewModel() {
    private val logoutDecisionChannel = Channel<LogoutDecision>(Channel.CONFLATED)
    val logoutDecisions: Flow<LogoutDecision> = logoutDecisionChannel.receiveAsFlow()
    private var logoutCheckJob: Job? = null

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

enum class LogoutDecision {
    WARN_UNSYNCED,
    PROCEED,
}
