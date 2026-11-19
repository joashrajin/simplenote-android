package com.automattic.simplenote.models

import com.simperium.client.FullTextIndex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NoteFullTextIndexerTest {
    private val mIndexer: FullTextIndex.Indexer<Note> = NoteFullTextIndexer()

    @Test
    fun indexesAreTheNoteFullTextIndexes() {
        assertSame(Note.FULL_TEXT_INDEXES, NoteFullTextIndexer.INDEXES)
        assertArrayEquals(arrayOf("title", "content"), NoteFullTextIndexer.INDEXES)
    }

    @Test
    fun indexesTitleAndContent() {
        val note = Note("key")
        note.content = "Groceries\nmilk\neggs"

        val expected = HashMap<String, String>()
        expected["title"] = "Groceries"
        expected["content"] = "Groceries\nmilk\neggs"
        assertEquals(expected, mIndexer.index(NoteFullTextIndexer.INDEXES, note))
    }

    @Test
    fun indexesASingleLineNoteAsBothTitleAndContent() {
        val note = Note("key")
        note.content = "  only line  "

        val index = mIndexer.index(NoteFullTextIndexer.INDEXES, note)

        assertEquals("only line", index["title"])
        assertEquals("  only line  ", index["content"])
    }

    @Test
    fun indexesTheMarkdownStrippedTitle() {
        val note = Note("key")
        note.isMarkdownEnabled = true
        note.content = "# Heading\nbody"

        val index = mIndexer.index(NoteFullTextIndexer.INDEXES, note)

        assertEquals("Heading", index["title"])
        assertEquals("# Heading\nbody", index["content"])
    }

    @Test
    fun indexesAnEmptyNoteAsEmptyStrings() {
        val index = mIndexer.index(NoteFullTextIndexer.INDEXES, Note("key"))

        assertEquals(2, index.size)
        assertEquals("", index["title"])
        assertEquals("", index["content"])
    }

    @Test
    fun alwaysIndexesBothFieldsWhateverKeysArePassed() {
        val note = Note("key")
        note.content = "Title\nBody"

        val index = mIndexer.index(arrayOf(), note)

        assertEquals(2, index.size)
        assertEquals("Title", index["title"])
        assertEquals("Title\nBody", index["content"])
    }

    @Test(expected = NullPointerException::class)
    fun nullNoteThrows() {
        mIndexer.index(NoteFullTextIndexer.INDEXES, null)
    }
}
