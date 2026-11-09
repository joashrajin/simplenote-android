package com.automattic.simplenote

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
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
    fun allNotesForExportDeliversTheRepositoryList() = runTest {
        val notes = listOf(Note("a"), Note("b"))
        val repository = mock<NotesRepository> {
            onBlocking { allNotesForExport() } doReturn notes
        }
        val received = mutableListOf<List<Note>>()

        PreferencesStreams.allNotesForExport(backgroundScope, repository) { received.add(it) }
        runCurrent()

        assertEquals(listOf(notes), received)
    }
}
