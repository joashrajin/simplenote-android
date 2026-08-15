package com.automattic.simplenote.utils

import androidx.annotation.MainThread
import com.automattic.simplenote.Simplenote
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportNotesGate @Inject constructor() {
    private val exportMutex = Mutex()
    private val stateLock = Any()
    private var generation = 0L
    private var resetInProgress = false

    fun currentGeneration(): Long = synchronized(stateLock) { generation }

    suspend fun <T> runExport(block: suspend () -> T): T {
        exportMutex.lock()
        try {
            return block()
        } finally {
            exportMutex.unlock()
        }
    }

    fun ensureCurrent(expectedGeneration: Long) {
        if (currentGeneration() != expectedGeneration) {
            throw ExportSessionChangedException()
        }
    }

    @MainThread
    fun logOut(application: Simplenote) {
        if (!beginSessionReset()) {
            return
        }

        try {
            AuthUtils.logOutImmediately(application)
        } finally {
            finishSessionReset()
        }
    }

    internal fun invalidateSession() {
        synchronized(stateLock) {
            generation++
        }
    }

    internal fun beginSessionReset(): Boolean = synchronized(stateLock) {
        if (resetInProgress) {
            false
        } else {
            resetInProgress = true
            generation++
            true
        }
    }

    internal fun finishSessionReset() {
        synchronized(stateLock) {
            resetInProgress = false
        }
    }
}

class ExportSessionChangedException : IOException(
    "The account session changed during note export",
)
