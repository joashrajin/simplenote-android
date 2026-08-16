package com.automattic.simplenote.models;

import com.simperium.client.BucketSchema.Index;
import com.simperium.client.BucketSchema.Indexer;

import java.util.ArrayList;
import java.util.List;

public class NoteIndexer implements Indexer<Note> {

    @Override
    public List<Index> index(Note note) {

        List<Index> indexes = new ArrayList<>();
        indexes.add(new Index(Note.PINNED_INDEX_NAME, note.isPinned()));
        indexes.add(new Index(Note.CONTENT_PREVIEW_INDEX_NAME, note.getContentPreview()));
        indexes.add(new Index(Note.TITLE_INDEX_NAME, note.getTitle()));
        indexes.add(new Index(Note.MODIFIED_INDEX_NAME, getDateIndex(
                note,
                Note.MODIFICATION_DATE_PROPERTY,
                Note.CREATION_DATE_PROPERTY
        )));
        indexes.add(new Index(Note.CREATED_INDEX_NAME, getDateIndex(
                note,
                Note.CREATION_DATE_PROPERTY,
                Note.MODIFICATION_DATE_PROPERTY
        )));
        return indexes;

    }

    private static long getDateIndex(Note note, String primaryProperty, String fallbackProperty) {
        Object date = note.getProperty(primaryProperty);
        if (!(date instanceof Number)) {
            date = note.getProperty(fallbackProperty);
        }
        if (!(date instanceof Number)) {
            return 0L;
        }
        return Note.numberToDate((Number) date).getTimeInMillis();
    }

}
