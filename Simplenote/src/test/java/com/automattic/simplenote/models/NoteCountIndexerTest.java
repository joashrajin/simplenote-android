package com.automattic.simplenote.models;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simperium.client.Bucket;
import com.simperium.client.BucketSchema.Index;
import com.simperium.client.Query;

import org.junit.Test;

import java.util.List;

public class NoteCountIndexerTest {
    private static final int NOTE_COUNT = 3;

    @SuppressWarnings("unchecked")
    @Test
    public void indexQueriesNotesByLexicalTagName() {
        Bucket<Note> notesBucket = mock(Bucket.class);
        Query<Note> query = new Query<>(notesBucket);
        when(notesBucket.query()).thenReturn(query);
        when(notesBucket.count(query)).thenReturn(NOTE_COUNT);
        Tag tag = new Tag("project%2Dalpha");
        tag.setName("project-alpha");

        List<Index> indexes = new NoteCountIndexer(notesBucket).index(tag);

        verify(notesBucket).count(query);
        List<Query.Condition> conditions = query.getConditions();
        assertEquals(2, conditions.size());
        Query.Condition tagCondition = conditions.get(1);
        assertEquals(Note.TAGS_PROPERTY, tagCondition.getKey());
        assertEquals(Query.ComparisonType.EQUAL_TO, tagCondition.getComparisonType());
        assertEquals("project-alpha", tagCondition.getSubject());
        assertEquals(1, indexes.size());
        assertEquals(Tag.NOTE_COUNT_INDEX_NAME, indexes.get(0).getName());
        assertEquals(NOTE_COUNT, indexes.get(0).getValue());
    }
}
