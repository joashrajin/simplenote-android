package com.automattic.simplenote

import com.automattic.simplenote.models.Note
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.TagsRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.text.ParseException

/**
 * Pins the importer's legacy pipeline against the repositories: construction through
 * [NotesRepository.noteForImport], every note of a JSON export built before the first save, tag
 * creation through [TagsRepository.saveTag] with rejected tags dropped from the note, exactly one
 * closing save per note, and the ImportException reasons.
 */
class ImporterTest {

    private val notesRepository = mock<NotesRepository>()
    private val tagsRepository = mock<TagsRepository>()
    private val importer = Importer(notesRepository, tagsRepository)

    @Test
    fun plaintextImportBuildsTheNoteAndSavesItOnce() {
        val note = noteWithTags()
        whenever(notesRepository.noteForImport("plain text")).thenReturn(note)

        importer.dispatchFileImport("txt", "plain text")

        inOrder(notesRepository) {
            verify(notesRepository).noteForImport("plain text")
            verify(notesRepository).saveImportedNote(note)
        }
        verify(note, never()).enableMarkdown()
        verifyNoInteractions(tagsRepository)
    }

    @Test
    fun markdownImportEnablesMarkdownBeforeTheSave() {
        val note = noteWithTags()
        whenever(notesRepository.noteForImport("# heading")).thenReturn(note)

        importer.dispatchFileImport("md", "# heading")

        inOrder(notesRepository, note) {
            verify(notesRepository).noteForImport("# heading")
            verify(note).enableMarkdown()
            verify(notesRepository).saveImportedNote(note)
        }
    }

    @Test
    fun jsonImportBuildsEveryNoteBeforeTheFirstSaveAndTrashesTrashedNotes() {
        val active1 = noteWithTags()
        val active2 = noteWithTags()
        val trashed = noteWithTags()
        whenever(notesRepository.noteForImport(any<JSONObject>())).thenReturn(active1, active2, trashed)

        importer.dispatchFileImport(
            "json",
            """{"activeNotes":[{"content":"a"},{"content":"b"}],"trashedNotes":[{"content":"c"}]}"""
        )

        inOrder(notesRepository, trashed) {
            verify(notesRepository, times(3)).noteForImport(any<JSONObject>())
            verify(trashed).setDeleted(true)
            verify(notesRepository).saveImportedNote(active1)
            verify(notesRepository).saveImportedNote(active2)
            verify(notesRepository).saveImportedNote(trashed)
        }
        verify(active1, never()).setDeleted(any())
        verify(active2, never()).setDeleted(any())
    }

    @Test
    fun jsonImportCreatesTagsBeforeTheSaveAndDropsTheOnesTheBucketRejects() {
        val note = noteWithTags("good", "bad")
        whenever(notesRepository.noteForImport(any<JSONObject>())).thenReturn(note)
        whenever(tagsRepository.saveTag("good")).thenReturn(true)
        whenever(tagsRepository.saveTag("bad")).thenReturn(false)

        importer.dispatchFileImport("json", """{"activeNotes":[{"content":"a","tags":["good","bad"]}]}""")

        inOrder(tagsRepository, notesRepository, note) {
            verify(tagsRepository).saveTag("good")
            verify(tagsRepository).saveTag("bad")
            verify(note).removeTag("bad")
            verify(notesRepository).saveImportedNote(note)
        }
        verify(note, never()).removeTag("good")
    }

    @Test
    fun anUnknownExportTypeThrowsWithoutTouchingTheRepositories() {
        val exception = assertThrows(Importer.ImportException::class.java) {
            importer.dispatchFileImport("pdf", "whatever")
        }

        assertEquals(Importer.FailureReason.UnknownExportType, exception.reason)
        verifyNoInteractions(notesRepository, tagsRepository)
    }

    @Test
    fun malformedJsonThrowsParseErrorWithoutSaving() {
        val exception = assertThrows(Importer.ImportException::class.java) {
            importer.dispatchFileImport("json", "not json at all")
        }

        assertEquals(Importer.FailureReason.ParseError, exception.reason)
        verify(notesRepository, never()).saveImportedNote(any())
    }

    @Test
    fun aJsonNoteThatFailsToParseAbortsTheImportBeforeAnySave() {
        val firstNote = noteWithTags()
        whenever(notesRepository.noteForImport(any<JSONObject>()))
            .thenReturn(firstNote)
            .thenThrow(ParseException("bad date", 0))

        val exception = assertThrows(Importer.ImportException::class.java) {
            importer.dispatchFileImport("json", """{"activeNotes":[{"content":"a"},{"content":"b"}]}""")
        }

        assertEquals(Importer.FailureReason.ParseError, exception.reason)
        verify(notesRepository, never()).saveImportedNote(any())
        verifyNoInteractions(tagsRepository)
    }

    private fun noteWithTags(vararg tagNames: String): Note = mock {
        on { tags } doReturn tagNames.toList()
    }
}
