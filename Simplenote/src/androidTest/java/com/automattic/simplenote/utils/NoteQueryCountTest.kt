package com.automattic.simplenote.utils

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.models.Note
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class NoteQueryCountTest {
    private lateinit var notesBucket: TestBucket<Note>

    @Before
    fun setUp() {
        notesBucket = object : TestBucket<Note>("notes") {
            override fun build(key: String?): Note = Note(key)
        }
    }

    @Test
    fun countMatchesExecutedCursorCountForWidgetExistenceProbe() {
        createNote("live-1", false)
        createNote("live-2", false)
        createNote("deleted-1", true)

        val query = Note.all(notesBucket)
        val executedCount = query.execute().use { it.count }

        assertEquals(executedCount, query.count())
        assertEquals(2, query.count())
    }

    @Test
    fun countMatchesExecutedCursorCountForEmptyBucket() {
        val query = Note.all(notesBucket)
        val executedCount = query.execute().use { it.count }

        assertEquals(executedCount, query.count())
        assertEquals(0, query.count())
    }

    private fun createNote(key: String, deleted: Boolean) {
        notesBucket.newObject(key).apply {
            setContent("content")
            setDeleted(deleted)
        }
    }
}
