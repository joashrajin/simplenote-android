package com.automattic.simplenote

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock

/**
 * The LogOutTask and export-scan replacement semantics: results deliver through the given
 * lifecycle scope and die with it, the way the legacy WeakReference guards dropped work for a
 * dead fragment.
 */
@ExperimentalCoroutinesApi
class PreferencesStreamsTest {

    @Test
    fun hasUnsyncedNotesDeliversTheRepositoryAnswer() = runTest {
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doReturn true
        }
        val received = mutableListOf<Boolean>()

        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository) { received.add(it) }
        runCurrent()

        assertEquals(listOf(true), received)
    }

    @Test
    fun hasUnsyncedNotesIsDroppedWithACancelledScope() = runTest {
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doReturn true
        }
        val received = mutableListOf<Boolean>()

        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository) { received.add(it) }
        backgroundScope.cancel()
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun exportNotesWritesTheRepositoryListAndToastsTheWriteResult() = runTest {
        val notes = listOf(Note("a"), Note("b"))
        val repository = mock<NotesRepository> {
            onBlocking { allNotesForExport() } doReturn notes
        }
        val written = mutableListOf<List<Note>>()
        val toasts = mutableListOf<Boolean>()

        PreferencesStreams.exportNotes(
            backgroundScope,
            repository,
            { list ->
                written.add(list)
                false
            },
            { toasts.add(it) },
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        assertEquals(listOf(notes), written)
        assertEquals(listOf(false), toasts)
    }

    @Test
    fun anExportAlreadyStartedSurvivesCancellationButSkipsTheToast() = runTest {
        // The legacy export wrote synchronously once the user picked a file; a fragment dying
        // mid-export must still produce the file, losing only the toast.
        val scopeDispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + scopeDispatcher)
        val gate = CompletableDeferred<Unit>()
        val repository = mock<NotesRepository> {
            onBlocking { allNotesForExport() } doSuspendableAnswer {
                gate.await()
                emptyList()
            }
        }
        var wrote = false
        var toasted = false

        PreferencesStreams.exportNotes(
            scope,
            repository,
            {
                wrote = true
                true
            },
            { toasted = true },
            StandardTestDispatcher(testScheduler),
        )
        scope.cancel()
        gate.complete(Unit)
        runCurrent()

        assertTrue(wrote)
        assertFalse(toasted)
    }
}
