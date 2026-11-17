package com.automattic.simplenote.models;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.simperium.client.FullTextIndex;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class NoteFullTextIndexerTest {
    private final FullTextIndex.Indexer<Note> mIndexer = new NoteFullTextIndexer();

    @Test
    public void indexesAreTheNoteFullTextIndexes() {
        assertSame(Note.FULL_TEXT_INDEXES, NoteFullTextIndexer.INDEXES);
        assertArrayEquals(new String[]{"title", "content"}, NoteFullTextIndexer.INDEXES);
    }

    @Test
    public void commaConstant() {
        assertEquals(", ", NoteFullTextIndexer.COMMA);
    }

    @Test
    public void indexesTitleAndContent() {
        Note note = new Note("key");
        note.setContent("Groceries\nmilk\neggs");

        Map<String, String> expected = new HashMap<>();
        expected.put("title", "Groceries");
        expected.put("content", "Groceries\nmilk\neggs");
        assertEquals(expected, mIndexer.index(NoteFullTextIndexer.INDEXES, note));
    }

    @Test
    public void indexesASingleLineNoteAsBothTitleAndContent() {
        Note note = new Note("key");
        note.setContent("  only line  ");

        Map<String, String> index = mIndexer.index(NoteFullTextIndexer.INDEXES, note);

        assertEquals("only line", index.get("title"));
        assertEquals("  only line  ", index.get("content"));
    }

    @Test
    public void indexesTheMarkdownStrippedTitle() {
        Note note = new Note("key");
        note.setMarkdownEnabled(true);
        note.setContent("# Heading\nbody");

        Map<String, String> index = mIndexer.index(NoteFullTextIndexer.INDEXES, note);

        assertEquals("Heading", index.get("title"));
        assertEquals("# Heading\nbody", index.get("content"));
    }

    @Test
    public void indexesAnEmptyNoteAsEmptyStrings() {
        Map<String, String> index = mIndexer.index(NoteFullTextIndexer.INDEXES, new Note("key"));

        assertEquals(2, index.size());
        assertEquals("", index.get("title"));
        assertEquals("", index.get("content"));
    }

    @Test
    public void alwaysIndexesBothFieldsWhateverKeysArePassed() {
        Note note = new Note("key");
        note.setContent("Title\nBody");

        Map<String, String> index = mIndexer.index(new String[0], note);

        assertEquals(2, index.size());
        assertEquals("Title", index.get("title"));
        assertEquals("Title\nBody", index.get("content"));
    }

    @Test(expected = NullPointerException.class)
    public void nullNoteThrows() {
        mIndexer.index(NoteFullTextIndexer.INDEXES, null);
    }
}
