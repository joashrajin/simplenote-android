package com.automattic.simplenote

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doThrow
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

        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, null) { received.add(it) }
        runCurrent()

        assertEquals(listOf(true), received)
    }

    @Test
    fun hasUnsyncedNotesIsDroppedWithACancelledScope() = runTest {
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doReturn true
        }
        val received = mutableListOf<Boolean>()

        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, null) { received.add(it) }
        backgroundScope.cancel()
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun hasUnsyncedNotesFailsClosedWhenTheRepositoryThrows() = runTest {
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doThrow IllegalStateException("cursor closed")
        }
        val received = mutableListOf<Boolean>()

        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, null) { received.add(it) }
        runCurrent()

        assertEquals(listOf(true), received)
    }

    @Test
    fun hasUnsyncedNotesDeliversNothingWhenTheCheckIsCancelled() = runTest {
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doThrow CancellationException("scope cancelled")
        }
        val received = mutableListOf<Boolean>()

        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, null) { received.add(it) }
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun aSecondCheckWhileOneIsInFlightIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        var queries = 0
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doSuspendableAnswer {
                queries++
                gate.await()
                true
            }
        }
        val received = mutableListOf<Boolean>()

        val first = PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, null) { received.add(it) }
        runCurrent()
        val second = PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, first) { received.add(it) }
        gate.complete(Unit)
        runCurrent()

        assertSame(first, second)
        assertEquals(1, queries)
        assertEquals(listOf(true), received)
    }

    @Test
    fun aCheckRunsAgainOnceThePreviousOneFinishedOrFailed() = runTest {
        var queries = 0
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doSuspendableAnswer {
                if (queries++ == 0) throw IllegalStateException("cursor closed") else false
            }
        }
        val received = mutableListOf<Boolean>()

        val failed = PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, null) { received.add(it) }
        runCurrent()
        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, failed) { received.add(it) }
        runCurrent()

        assertEquals(listOf(true, false), received)
    }

    @Test
    fun aCheckRunsAgainOnceTheViewScopeOfThePreviousOneDied() = runTest {
        val viewScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val repository = mock<NotesRepository> {
            onBlocking { hasUnsyncedNotes() } doSuspendableAnswer {
                gate.await()
                true
            }
        }
        val received = mutableListOf<String>()

        val stale = PreferencesStreams.hasUnsyncedNotes(viewScope, repository, null) { received.add("stale") }
        viewScope.cancel()
        PreferencesStreams.hasUnsyncedNotes(backgroundScope, repository, stale) { received.add("fresh") }
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("fresh"), received)
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

    @Test
    fun exportNotesToastsFailureWhenTheRepositoryThrows() = runTest {
        val repository = mock<NotesRepository> {
            onBlocking { allNotesForExport() } doThrow IllegalStateException("cursor closed")
        }
        var wrote = false
        val toasts = mutableListOf<Boolean>()

        PreferencesStreams.exportNotes(
            backgroundScope,
            repository,
            {
                wrote = true
                true
            },
            { toasts.add(it) },
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        assertFalse(wrote)
        assertEquals(listOf(false), toasts)
    }

    @Test
    fun exportNotesToastsFailureWhenTheWriteThrows() = runTest {
        val repository = mock<NotesRepository> {
            onBlocking { allNotesForExport() } doReturn listOf(Note("a"))
        }
        val toasts = mutableListOf<Boolean>()

        PreferencesStreams.exportNotes(
            backgroundScope,
            repository,
            { throw IllegalStateException("provider gone") },
            { toasts.add(it) },
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        assertEquals(listOf(false), toasts)
    }
}
