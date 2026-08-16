package com.automattic.simplenote

import com.automattic.simplenote.models.Note
import com.simperium.client.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NoteEditorFragmentTest {
    private lateinit var notesBucket: Bucket<Note>
    private lateinit var cursor: Bucket.ObjectCursor<Note>

    @Before
    fun setUp() {
        notesBucket = mock()
        cursor = mock()
        whenever(notesBucket.allObjects()).thenReturn(cursor)
    }

    @Test
    fun findNoteInObjectStoreReturnsTheFirstExactMatchAndClosesTheCursor() {
        val otherNote = note("other")
        val matchingNote = note("target")
        whenever(cursor.moveToNext()).thenReturn(true, true, false)
        whenever(cursor.getObject()).thenReturn(otherNote, matchingNote)

        val result = NoteEditorFragment.findNoteInObjectStore(notesBucket, "target")

        assertSame(matchingNote, result)
        verify(cursor, times(2)).moveToNext()
        verify(cursor, times(2)).getObject()
        verify(cursor).close()
    }

    @Test
    fun findNoteInObjectStoreReturnsNullAndClosesTheCursorWhenMissing() {
        val otherNote = note("other")
        whenever(cursor.moveToNext()).thenReturn(true, false)
        whenever(cursor.getObject()).thenReturn(otherNote)

        val result = NoteEditorFragment.findNoteInObjectStore(notesBucket, "target")

        assertNull(result)
        verify(cursor).close()
    }

    @Test
    fun findNoteInObjectStorePropagatesCloseFailureFromAMatchingReturn() {
        val matchingNote = note("target")
        val closeFailure = IllegalStateException("close failed")
        whenever(cursor.moveToNext()).thenReturn(true)
        whenever(cursor.getObject()).thenReturn(matchingNote)
        doThrow(closeFailure).whenever(cursor).close()

        val thrown = assertThrows(IllegalStateException::class.java) {
            NoteEditorFragment.findNoteInObjectStore(notesBucket, "target")
        }

        assertSame(closeFailure, thrown)
        verify(cursor).close()
    }

    @Test
    fun findNoteInObjectStorePreservesTraversalFailureWhenCloseAlsoFails() {
        val traversalFailure = IllegalStateException("traversal failed")
        val closeFailure = IllegalStateException("close failed")
        whenever(cursor.moveToNext()).thenReturn(true)
        whenever(cursor.getObject()).thenThrow(traversalFailure)
        doThrow(closeFailure).whenever(cursor).close()

        val thrown = assertThrows(IllegalStateException::class.java) {
            NoteEditorFragment.findNoteInObjectStore(notesBucket, "target")
        }

        assertSame(traversalFailure, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertSame(closeFailure, thrown.suppressed.single())
        verify(cursor).close()
    }

    private fun note(key: String): Note = mock {
        on { simperiumKey }.thenReturn(key)
    }
}
