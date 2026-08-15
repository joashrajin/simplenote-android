package com.automattic.simplenote.usecases

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import com.automattic.simplenote.search.SortOrder
import com.simperium.client.Bucket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class LoadNoteWidgetPickerUseCaseTest {
    private val notesRepository = mock<NotesRepository>()
    private val preferencesRepository = mock<PreferencesRepository>()
    private val preferencesProvider = RecordingProvider(preferencesRepository)

    @Test
    fun loadItemsUsesTheCurrentSortAndMapsOnlyIndexedRowsOnIo() = runTest {
        val ioDispatcher = QueueingDispatcher()
        val cursor = pickerCursor(
            keys = listOf("a", "b"),
            titles = listOf("Title A", null),
            previews = listOf("Preview A", null),
        )
        whenever(preferencesRepository.sortOrder()).thenReturn(SortOrder.CREATED_ASC)
        whenever(notesRepository.search(any())).thenReturn(NoteQueryResult.Notes(cursor, null))
        val useCase = useCase(ioDispatcher)

        val result = async { useCase.loadItems() }
        assertEquals(0, preferencesProvider.calls)
        runCurrent()
        assertEquals(0, preferencesProvider.calls)
        ioDispatcher.runAll()
        runCurrent()

        assertEquals(
            listOf(
                WidgetNotePickerItem("a", "Title A", "Preview A"),
                WidgetNotePickerItem("b", "", ""),
            ),
            result.await(),
        )
        assertEquals(1, preferencesProvider.calls)
        val request = argumentCaptor<NoteSearchRequest>()
        verify(notesRepository).search(request.capture())
        assertEquals(NoteFilter.AllNotes, request.firstValue.filter)
        assertNull(request.firstValue.rawSearch)
        assertEquals(SortOrder.CREATED_ASC, request.firstValue.sort)
        assertTrue(request.firstValue.pinnedFirst)
        verify(cursor, never()).getObject()
        verify(cursor).close()
    }

    @Test
    fun loadItemsClosesTheCursorWhenIndexedMappingFails() = runTest {
        val cursor = mock<Bucket.ObjectCursor<Note>>()
        whenever(cursor.getColumnIndexOrThrow(Note.TITLE_INDEX_NAME)).thenReturn(1)
        whenever(cursor.getColumnIndexOrThrow(Note.CONTENT_PREVIEW_INDEX_NAME)).thenReturn(2)
        whenever(cursor.moveToNext()).thenReturn(true)
        whenever(cursor.simperiumKey).thenReturn("a")
        whenever(cursor.getString(1)).thenThrow(IllegalStateException("mapping failed"))
        whenever(preferencesRepository.sortOrder()).thenReturn(SortOrder.MODIFIED_DESC)
        whenever(notesRepository.search(any())).thenReturn(NoteQueryResult.Notes(cursor, null))

        val failure = runCatching { useCase(StandardTestDispatcher(testScheduler)).loadItems() }

        assertEquals("mapping failed", failure.exceptionOrNull()?.message)
        verify(cursor).close()
    }

    @Test
    fun loadItemsClosesTheCursorWhenItsCallerIsCancelled() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val cursor = mock<Bucket.ObjectCursor<Note>>()
        whenever(cursor.getColumnIndexOrThrow(Note.TITLE_INDEX_NAME)).thenReturn(1)
        whenever(cursor.getColumnIndexOrThrow(Note.CONTENT_PREVIEW_INDEX_NAME)).thenReturn(2)
        whenever(cursor.moveToNext()).thenReturn(true, true)
        whenever(cursor.getString(1)).thenReturn("Title")
        whenever(cursor.getString(2)).thenReturn("Preview")
        whenever(preferencesRepository.sortOrder()).thenReturn(SortOrder.MODIFIED_DESC)
        whenever(notesRepository.search(any())).thenReturn(NoteQueryResult.Notes(cursor, null))
        lateinit var result: kotlinx.coroutines.Deferred<List<WidgetNotePickerItem>>
        whenever(cursor.simperiumKey).thenAnswer {
            result.cancel()
            "a"
        }

        result = async { useCase(ioDispatcher).loadItems() }
        advanceUntilIdle()

        assertTrue(result.isCancelled)
        verify(cursor).close()
    }

    @Test
    fun anInvalidQueryFailsInsteadOfRenderingAnEmptyNotebook() = runTest {
        whenever(preferencesRepository.sortOrder()).thenReturn(SortOrder.MODIFIED_DESC)
        whenever(notesRepository.search(any())).thenReturn(NoteQueryResult.InvalidQuery)

        val result = runCatching { useCase(StandardTestDispatcher(testScheduler)).loadItems() }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun loadSelectionCopiesTheFreshNoteWithoutResolvingPreferences() = runTest {
        val note = mock<Note>()
        whenever(note.simperiumKey).thenReturn("a")
        whenever(note.title).thenReturn("Title")
        whenever(note.content).thenReturn("Title\nBody")
        whenever(note.isMarkdownEnabled).thenReturn(true)
        whenever(note.isPreviewEnabled).thenReturn(false)
        whenever(notesRepository.getNote("a")).thenReturn(note)

        val selection = useCase(StandardTestDispatcher(testScheduler)).loadSelection("a")

        assertEquals(
            WidgetNotePickerSelection(
                key = "a",
                title = "Title",
                content = "Title\nBody",
                markdownEnabled = true,
                previewEnabled = false,
            ),
            selection,
        )
        assertEquals(0, preferencesProvider.calls)
    }

    @Test
    fun loadSelectionReturnsNullWhenTheListedNoteWasDeleted() = runTest {
        whenever(notesRepository.getNote("missing")).thenReturn(null)

        val selection = useCase(StandardTestDispatcher(testScheduler)).loadSelection("missing")

        assertNull(selection)
        assertFalse(preferencesProvider.calls > 0)
    }

    private fun useCase(ioDispatcher: CoroutineDispatcher) = LoadNoteWidgetPickerUseCase(
        notesRepository,
        preferencesProvider,
        ioDispatcher,
    )

    private fun pickerCursor(
        keys: List<String>,
        titles: List<String?>,
        previews: List<String?>,
    ): Bucket.ObjectCursor<Note> {
        val cursor = mock<Bucket.ObjectCursor<Note>>()
        whenever(cursor.getColumnIndexOrThrow(Note.TITLE_INDEX_NAME)).thenReturn(1)
        whenever(cursor.getColumnIndexOrThrow(Note.CONTENT_PREVIEW_INDEX_NAME)).thenReturn(2)
        val moves = List(keys.size) { true } + false
        whenever(cursor.moveToNext()).thenReturn(moves.first(), *moves.drop(1).toTypedArray())
        whenever(cursor.simperiumKey).thenReturn(keys.first(), *keys.drop(1).toTypedArray())
        whenever(cursor.getString(1)).thenReturn(titles.first(), *titles.drop(1).toTypedArray())
        whenever(cursor.getString(2)).thenReturn(previews.first(), *previews.drop(1).toTypedArray())
        return cursor
    }

    private class RecordingProvider<T>(private val value: T) : Provider<T> {
        var calls = 0

        override fun get(): T {
            calls++
            return value
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
