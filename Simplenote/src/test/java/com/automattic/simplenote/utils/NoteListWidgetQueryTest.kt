package com.automattic.simplenote.utils

import com.automattic.simplenote.models.Note
import com.simperium.client.Bucket
import com.simperium.client.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NoteListWidgetQueryTest {
    private lateinit var notesBucket: Bucket<Note>

    @Before
    fun setUp() {
        notesBucket = mock()
        whenever(notesBucket.query()).thenAnswer { Query<Note>(notesBucket) }
    }

    @Test
    fun activeNotesCountUsesOnlyTheDeletedFilter() {
        whenever(notesBucket.count(any<Query<Note>>())).thenReturn(2)

        assertTrue(NoteListWidgetQuery.hasActiveNotes(notesBucket))

        val query = argumentCaptor<Query<Note>>().run {
            verify(notesBucket).count(capture())
            firstValue
        }
        assertEquals(
            listOf("deleted NOT_EQUAL_TO true"),
            query.conditions.map { condition ->
                "${condition.key} ${condition.comparisonType.name} ${condition.subject}"
            }
        )
        assertEquals(emptyList<String>(), query.fields.map { field -> field.name })
        assertEquals(emptyList<String>(), query.sorters.map { sorter -> sorter.key })
    }

    @Test
    fun noActiveNotesWhenTheCountIsZero() {
        whenever(notesBucket.count(any<Query<Note>>())).thenReturn(0)

        assertFalse(NoteListWidgetQuery.hasActiveNotes(notesBucket))

        verify(notesBucket).count(any<Query<Note>>())
    }
}
