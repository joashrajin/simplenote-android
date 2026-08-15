package com.automattic.simplenote.usecases

import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NoteQueryResult
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.search.NoteFilter
import com.automattic.simplenote.search.NoteSearchRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class LoadNoteWidgetPickerUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
    private val preferencesRepositoryProvider: Provider<PreferencesRepository>,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun loadItems(): List<WidgetNotePickerItem> = withContext(ioDispatcher) {
        val sortOrder = preferencesRepositoryProvider.get().sortOrder()
        val request = NoteSearchRequest(
            filter = NoteFilter.AllNotes,
            rawSearch = null,
            sort = sortOrder,
            pinnedFirst = true,
        )

        when (val result = notesRepository.search(request)) {
            NoteQueryResult.InvalidQuery -> error("The widget note query is invalid")
            is NoteQueryResult.Notes -> result.cursor.use { cursor ->
                val titleIndex = cursor.getColumnIndexOrThrow(Note.TITLE_INDEX_NAME)
                val previewIndex = cursor.getColumnIndexOrThrow(Note.CONTENT_PREVIEW_INDEX_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        add(
                            WidgetNotePickerItem(
                                key = cursor.simperiumKey,
                                title = cursor.getString(titleIndex).orEmpty(),
                                preview = cursor.getString(previewIndex).orEmpty(),
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun loadSelection(key: String): WidgetNotePickerSelection? = withContext(ioDispatcher) {
        notesRepository.getNote(key)?.let { note ->
            WidgetNotePickerSelection(
                key = note.simperiumKey,
                title = note.title.orEmpty(),
                content = note.content.orEmpty(),
                markdownEnabled = note.isMarkdownEnabled,
                previewEnabled = note.isPreviewEnabled,
            )
        }
    }
}

data class WidgetNotePickerItem(
    val key: String,
    val title: String,
    val preview: String,
)

data class WidgetNotePickerSelection(
    val key: String,
    val title: String,
    val content: String,
    val markdownEnabled: Boolean,
    val previewEnabled: Boolean,
)
