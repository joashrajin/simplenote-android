package com.automattic.simplenote.usecases

import android.content.Context
import android.net.Uri
import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.utils.ExportNotesGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

class ExportNotesUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val exportNotesGate: ExportNotesGate,
) {
    suspend operator fun invoke(uri: Uri, unsyncedOnly: Boolean, generation: Long) = withContext(ioDispatcher) {
        exportNotesGate.ensureCurrent(generation)
        val notes = notesRepository.allNotesForExport()
        val activeNotes = JSONArray()
        val trashedNotes = JSONArray()

        for (note in notes) {
            currentCoroutineContext().ensureActive()
            if (unsyncedOnly && !note.isNew && !note.isModified) {
                continue
            }

            val noteJson = note.toExportJson()
            if (note.isDeleted) {
                trashedNotes.put(noteJson)
            } else {
                activeNotes.put(noteJson)
            }
        }

        val account = JSONObject()
            .put("activeNotes", activeNotes)
            .put("trashedNotes", trashedNotes)
        val bytes = account.toString(2).replace("\\/", "/").toByteArray(Charsets.UTF_8)

        currentCoroutineContext().ensureActive()
        exportNotesGate.ensureCurrent(generation)
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the export destination")
        output.use { stream -> stream.write(bytes) }
    }

    private fun Note.toExportJson(): JSONObject {
        val noteJson = JSONObject()
            .put("id", simperiumKey)
            .put("content", content)
            .put("creationDate", creationDateString)
            .put("lastModified", modificationDateString)

        if (hasExportSystemTag(Note.PINNED_TAG)) {
            noteJson.put("pinned", true)
        }
        if (hasExportSystemTag(Note.MARKDOWN_TAG)) {
            noteJson.put("markdown", true)
        }

        val sortedTags = exportTags().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (sortedTags.isNotEmpty()) {
            noteJson.put("tags", JSONArray(sortedTags))
        }
        if (publishedUrl.isNotEmpty()) {
            noteJson.put("publicURL", publishedUrl)
        }
        return noteJson
    }

    private fun Note.exportTags(): List<String> {
        val storedTags = getProperty(Note.TAGS_PROPERTY) as? JSONArray ?: return emptyList()
        val tags = mutableListOf<String>()
        for (index in 0 until storedTags.length()) {
            storedTags.optString(index).takeIf(String::isNotEmpty)?.let(tags::add)
        }
        return tags
    }

    private fun Note.hasExportSystemTag(tag: String): Boolean {
        val storedTags = getProperty(Note.SYSTEM_TAGS_PROPERTY) as? JSONArray ?: return false
        return (0 until storedTags.length()).any { index -> storedTags.optString(index) == tag }
    }
}
