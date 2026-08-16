package com.automattic.simplenote.models

import com.automattic.simplenote.utils.TagUtils
import com.simperium.client.Bucket
import com.simperium.client.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TagTest {
    private lateinit var tagsBucket: Bucket<Tag>
    private lateinit var notesBucket: Bucket<Note>
    private lateinit var cursor: Bucket.ObjectCursor<Note>
    private lateinit var note: Note
    private lateinit var tag: Tag

    @Before
    fun setUp() {
        tagsBucket = mock()
        notesBucket = mock()
        cursor = mock()
        note = mock()

        whenever(notesBucket.query()).thenAnswer { Query<Note>(notesBucket) }
        whenever(notesBucket.searchObjects(any())).thenReturn(cursor)
        whenever(cursor.moveToNext()).thenReturn(true, false)
        whenever(cursor.getObject()).thenReturn(note)
        whenever(note.tags).thenReturn(listOf(OLD_TAG))

        tag = Tag(TagUtils.hashTag(NEW_TAG)).apply {
            setBucket(tagsBucket)
            setName(OLD_TAG)
        }
    }

    @Test
    fun renameToClosesNotesCursorWhenNoteSaveFails() {
        val saveFailure = IllegalStateException("save failed")
        doThrow(saveFailure).whenever(note).save()

        val thrown = assertThrows(IllegalStateException::class.java) {
            tag.renameTo(OLD_TAG, NEW_TAG, 0, notesBucket)
        }

        assertSame(saveFailure, thrown)
        verify(cursor).close()
    }

    @Test
    fun renameToPreservesSaveFailureWhenCursorCloseAlsoFails() {
        val saveFailure = IllegalStateException("save failed")
        val closeFailure = IllegalStateException("close failed")
        doThrow(saveFailure).whenever(note).save()
        doThrow(closeFailure).whenever(cursor).close()

        val thrown = assertThrows(IllegalStateException::class.java) {
            tag.renameTo(OLD_TAG, NEW_TAG, 0, notesBucket)
        }

        assertSame(saveFailure, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertSame(closeFailure, thrown.suppressed.single())
        verify(cursor).close()
    }

    @Test
    fun renameToClosesNotesCursorOnceOnSuccess() {
        tag.renameTo(OLD_TAG, NEW_TAG, 0, notesBucket)

        verify(note).setTags(listOf(NEW_TAG))
        verify(note).save()
        verify(cursor).close()
        assertEquals(NEW_TAG, tag.getName())
    }

    private companion object {
        const val OLD_TAG = "work"
        const val NEW_TAG = "WORK"
    }
}
