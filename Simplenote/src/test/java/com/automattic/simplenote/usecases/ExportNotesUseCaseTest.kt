package com.automattic.simplenote.usecases

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.utils.ExportNotesGate
import com.automattic.simplenote.utils.ExportSessionChangedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Calendar
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class ExportNotesUseCaseTest {
    private val notesRepository = mock<NotesRepository>()
    private val context = mock<Context>()
    private val contentResolver = mock<ContentResolver>()
    private val uri = mock<Uri>()
    private val exportNotesGate = ExportNotesGate()
    private val generation = exportNotesGate.currentGeneration()

    @Before
    fun setUp() {
        whenever(context.contentResolver).thenReturn(contentResolver)
    }

    @Test
    fun fullExportPreservesSchemaCursorOrderAndOptionalFields() = runTest {
        val active = note("active", "café </script>", deleted = false).apply {
            setPinned(true)
            setMarkdownEnabled(true)
            setTags(listOf("zeta", "Alpha"))
            setProperty(Note.PUBLISH_URL_PROPERTY, "public-code")
        }
        val trash = exportNoteMock("trash", deleted = true)
        notesRepository.stub { onBlocking { allNotesForExport() }.doReturn(listOf(active, trash)) }
        val output = TrackingOutputStream()
        whenever(contentResolver.openOutputStream(uri, "wt")).thenReturn(output)

        useCase().invoke(uri, unsyncedOnly = false, generation = generation)

        val text = output.toString(Charsets.UTF_8.name())
        val account = JSONObject(text)
        val activeJson = account.getJSONArray("activeNotes").getJSONObject(0)
        val trashJson = account.getJSONArray("trashedNotes").getJSONObject(0)
        assertEquals(listOf("active"), ids(account, "activeNotes"))
        assertEquals(listOf("trash"), ids(account, "trashedNotes"))
        assertEquals("café </script>", activeJson.getString("content"))
        assertEquals(active.creationDateString, activeJson.getString("creationDate"))
        assertEquals(active.modificationDateString, activeJson.getString("lastModified"))
        assertTrue(activeJson.getBoolean("pinned"))
        assertTrue(activeJson.getBoolean("markdown"))
        assertEquals(listOf("Alpha", "zeta"), strings(activeJson, "tags"))
        assertEquals(active.publishedUrl, activeJson.getString("publicURL"))
        assertFalse(trashJson.has("pinned"))
        assertFalse(trashJson.has("markdown"))
        assertFalse(trashJson.has("tags"))
        assertFalse(trashJson.has("publicURL"))
        assertTrue(text.contains("\n  \"activeNotes\""))
        assertFalse(text.contains("\\/"))
        assertTrue(output.closed)
        verify(contentResolver, never()).delete(uri, null, null)
    }

    @Test
    fun exportDoesNotInitializeMissingTagProperties() = runTest {
        val directNote = note("direct", "content", deleted = false)
        notesRepository.stub { onBlocking { allNotesForExport() }.doReturn(listOf(directNote)) }
        val output = TrackingOutputStream()
        whenever(contentResolver.openOutputStream(uri, "wt")).thenReturn(output)

        useCase().invoke(uri, unsyncedOnly = false, generation = generation)

        val exported = JSONObject(output.toString(Charsets.UTF_8.name()))
            .getJSONArray("activeNotes")
            .getJSONObject(0)
        assertFalse(exported.has("pinned"))
        assertFalse(exported.has("markdown"))
        assertFalse(exported.has("tags"))
        assertEquals(null, directNote.getProperty(Note.TAGS_PROPERTY))
        assertEquals(null, directNote.getProperty(Note.SYSTEM_TAGS_PROPERTY))
    }

    @Test
    fun unsyncedExportIncludesNewAndModifiedNotesOnly() = runTest {
        val newNote = exportNoteMock("new", isNew = true)
        val modifiedNote = exportNoteMock("modified", isModified = true)
        val modifiedTrash = exportNoteMock("modified-trash", isModified = true, deleted = true)
        val syncedNote = exportNoteMock("synced")
        notesRepository.stub {
            onBlocking { allNotesForExport() }
                .doReturn(listOf(newNote, syncedNote, modifiedNote, modifiedTrash))
        }
        val output = TrackingOutputStream()
        whenever(contentResolver.openOutputStream(uri, "wt")).thenReturn(output)

        useCase().invoke(uri, unsyncedOnly = true, generation = generation)

        val account = JSONObject(output.toString(Charsets.UTF_8.name()))
        assertEquals(listOf("new", "modified"), ids(account, "activeNotes"))
        assertEquals(listOf("modified-trash"), ids(account, "trashedNotes"))
    }

    @Test
    fun anEmptyExportWritesBothEmptyArrays() = runTest {
        notesRepository.stub { onBlocking { allNotesForExport() }.doReturn(emptyList()) }
        val output = TrackingOutputStream()
        whenever(contentResolver.openOutputStream(uri, "wt")).thenReturn(output)

        useCase().invoke(uri, unsyncedOnly = false, generation = generation)

        val account = JSONObject(output.toString(Charsets.UTF_8.name()))
        assertTrue(ids(account, "activeNotes").isEmpty())
        assertTrue(ids(account, "trashedNotes").isEmpty())
    }

    @Test
    fun aNullOutputStreamFailsAfterSerialization() = runTest {
        notesRepository.stub { onBlocking { allNotesForExport() }.doReturn(emptyList()) }
        whenever(contentResolver.openOutputStream(uri, "wt")).thenReturn(null)

        expectFailure<IOException> {
            useCase().invoke(uri, unsyncedOnly = false, generation = generation)
        }

        verify(notesRepository).allNotesForExport()
        verify(contentResolver, never()).delete(uri, null, null)
    }

    @Test
    fun aWriteFailureClosesTheStreamAndPropagates() = runTest {
        notesRepository.stub { onBlocking { allNotesForExport() }.doReturn(emptyList()) }
        val output = FailingOutputStream()
        whenever(contentResolver.openOutputStream(uri, "wt")).thenReturn(output)

        expectFailure<IOException> {
            useCase().invoke(uri, unsyncedOnly = false, generation = generation)
        }

        assertTrue(output.closed)
        verify(contentResolver, never()).delete(uri, null, null)
    }

    @Test
    fun repositoryCancellationDoesNotOpenTheDestination() = runTest {
        notesRepository.stub {
            onBlocking { allNotesForExport() }.doSuspendableAnswer { throw CancellationException() }
        }

        expectFailure<CancellationException> {
            useCase().invoke(uri, unsyncedOnly = false, generation = generation)
        }

        verify(contentResolver, never()).openOutputStream(uri, "wt")
        verify(contentResolver, never()).delete(uri, null, null)
    }

    @Test
    fun aStaleSessionFailsWithoutReadingNotesOrOpeningTheDestination() = runTest {
        exportNotesGate.invalidateSession()

        expectFailure<ExportSessionChangedException> {
            useCase().invoke(uri, unsyncedOnly = false, generation = generation)
        }

        verify(notesRepository, never()).allNotesForExport()
        verify(contentResolver, never()).openOutputStream(uri, "wt")
        verify(contentResolver, never()).delete(uri, null, null)
    }

    @Test
    fun aSessionChangeDuringTheSnapshotFailsWithoutOpeningTheDestination() = runTest {
        notesRepository.stub {
            onBlocking { allNotesForExport() }.doSuspendableAnswer {
                exportNotesGate.invalidateSession()
                emptyList()
            }
        }

        expectFailure<ExportSessionChangedException> {
            useCase().invoke(uri, unsyncedOnly = false, generation = generation)
        }

        verify(contentResolver, never()).openOutputStream(uri, "wt")
        verify(contentResolver, never()).delete(uri, null, null)
    }

    @Test
    fun aSessionChangeAfterSnapshotValidationStillWritesTheImmutableSnapshot() = runTest {
        val note = note("old-session", "snapshot content", deleted = false)
        notesRepository.stub { onBlocking { allNotesForExport() }.doReturn(listOf(note)) }
        val output = TrackingOutputStream()
        whenever(contentResolver.openOutputStream(uri, "wt")).thenAnswer {
            exportNotesGate.invalidateSession()
            output
        }

        useCase().invoke(uri, unsyncedOnly = false, generation = generation)

        val account = JSONObject(output.toString(Charsets.UTF_8.name()))
        val exported = account.getJSONArray("activeNotes").getJSONObject(0)
        assertEquals("old-session", exported.getString("id"))
        assertEquals("snapshot content", exported.getString("content"))
        assertTrue(ids(account, "trashedNotes").isEmpty())
        assertTrue(output.closed)
    }

    @Test
    fun repositoryAndResolverWorkWaitForTheInjectedDispatcher() = runTest {
        notesRepository.stub { onBlocking { allNotesForExport() }.doReturn(emptyList()) }
        val output = TrackingOutputStream()
        whenever(contentResolver.openOutputStream(uri, "wt")).thenReturn(output)
        val dispatcher = QueueingDispatcher()
        val operation = async {
            ExportNotesUseCase(notesRepository, context, dispatcher, exportNotesGate)(uri, false, generation)
        }
        runCurrent()

        verify(notesRepository, never()).allNotesForExport()
        verify(contentResolver, never()).openOutputStream(uri, "wt")

        dispatcher.runAll()
        runCurrent()
        operation.await()

        verify(notesRepository).allNotesForExport()
        verify(contentResolver).openOutputStream(uri, "wt")
    }

    private fun useCase() = ExportNotesUseCase(
        notesRepository,
        context,
        UnconfinedTestDispatcher(),
        exportNotesGate,
    )

    private fun note(key: String, content: String, deleted: Boolean): Note = Note(key).apply {
        setContent(content)
        setCreationDate(calendar(1_700_000_000_000))
        setModificationDate(calendar(1_710_000_000_000))
        setDeleted(deleted)
    }

    private fun exportNoteMock(
        key: String,
        isNew: Boolean = false,
        isModified: Boolean = false,
        deleted: Boolean = false,
    ): Note =
        mock {
            on { simperiumKey } doReturn key
            on { content } doReturn key
            on { creationDateString } doReturn "2024-01-01T00:00:00.000Z"
            on { modificationDateString } doReturn "2024-01-02T00:00:00.000Z"
            on { isPinned } doReturn false
            on { isMarkdownEnabled } doReturn false
            on { tags } doReturn emptyList()
            on { publishedUrl } doReturn ""
            on { isDeleted } doReturn deleted
            on { this.isNew } doReturn isNew
            on { this.isModified } doReturn isModified
        }

    private fun calendar(timeMillis: Long) = Calendar.getInstance().apply {
        timeInMillis = timeMillis
    }

    private fun ids(account: JSONObject, key: String): List<String> {
        val array = account.getJSONArray(key)
        return (0 until array.length()).map { index -> array.getJSONObject(index).getString("id") }
    }

    private fun strings(objectJson: JSONObject, key: String): List<String> {
        val array = objectJson.getJSONArray(key)
        return (0 until array.length()).map(array::getString)
    }

    private suspend inline fun <reified T : Throwable> expectFailure(noinline block: suspend () -> Unit): T {
        try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is T) {
                return throwable
            }
            throw throwable
        }
        fail("Expected ${T::class.java.simpleName}")
        throw AssertionError("unreachable")
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class FailingOutputStream : OutputStream() {
        var closed = false

        override fun write(value: Int) {
            throw IOException("write failed")
        }

        override fun close() {
            closed = true
        }
    }

    private class QueueingDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }
}
