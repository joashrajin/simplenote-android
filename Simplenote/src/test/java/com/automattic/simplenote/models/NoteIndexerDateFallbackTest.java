package com.automattic.simplenote.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.simperium.client.BucketSchema.Index;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class NoteIndexerDateFallbackTest {
    private static final long CREATION_SECONDS = 1_379_018_823L;
    private static final long CREATION_MILLIS = 1_379_018_823_000L;
    private static final double MODIFICATION_MILLIS = 1_500_000_000_000.0;
    private static final long MODIFICATION_MILLIS_LONG = 1_500_000_000_000L;

    private final NoteIndexer mIndexer = new NoteIndexer();

    @Test
    public void numericDatesKeepTheirOwnIndexes() throws JSONException {
        List<Index> indexes = index(CREATION_SECONDS, MODIFICATION_MILLIS);

        assertIndex(indexes, Note.CREATED_INDEX_NAME, CREATION_MILLIS);
        assertIndex(indexes, Note.MODIFIED_INDEX_NAME, MODIFICATION_MILLIS_LONG);
    }

    @Test
    public void creationDateFallsBackToModificationDate() throws JSONException {
        List<Index> indexes = index(null, MODIFICATION_MILLIS);

        assertIndex(indexes, Note.CREATED_INDEX_NAME, MODIFICATION_MILLIS_LONG);
        assertIndex(indexes, Note.MODIFIED_INDEX_NAME, MODIFICATION_MILLIS_LONG);
    }

    @Test
    public void modificationDateFallsBackToCreationDate() throws JSONException {
        List<Index> indexes = index(CREATION_SECONDS, null);

        assertIndex(indexes, Note.CREATED_INDEX_NAME, CREATION_MILLIS);
        assertIndex(indexes, Note.MODIFIED_INDEX_NAME, CREATION_MILLIS);
    }

    @Test
    public void missingDatesUseEpoch() throws JSONException {
        List<Index> indexes = index(null, null);

        assertIndex(indexes, Note.CREATED_INDEX_NAME, 0L);
        assertIndex(indexes, Note.MODIFIED_INDEX_NAME, 0L);
    }

    @Test
    public void malformedDatesFallBackToTheOtherNumericDate() throws JSONException {
        List<Index> invalidCreation = index("invalid", MODIFICATION_MILLIS);
        List<Index> invalidModification = index(CREATION_SECONDS, "invalid");

        assertIndex(invalidCreation, Note.CREATED_INDEX_NAME, MODIFICATION_MILLIS_LONG);
        assertIndex(invalidCreation, Note.MODIFIED_INDEX_NAME, MODIFICATION_MILLIS_LONG);
        assertIndex(invalidModification, Note.CREATED_INDEX_NAME, CREATION_MILLIS);
        assertIndex(invalidModification, Note.MODIFIED_INDEX_NAME, CREATION_MILLIS);
    }

    @Test
    public void malformedDatesUseEpochWithoutANumericFallback() throws JSONException {
        List<Index> indexes = index("invalid creation", "invalid modification");

        assertIndex(indexes, Note.CREATED_INDEX_NAME, 0L);
        assertIndex(indexes, Note.MODIFIED_INDEX_NAME, 0L);
    }

    private List<Index> index(Object creationDate, Object modificationDate) throws JSONException {
        JSONObject properties = new JSONObject();
        if (creationDate != null) {
            properties.put(Note.CREATION_DATE_PROPERTY, creationDate);
        }
        if (modificationDate != null) {
            properties.put(Note.MODIFICATION_DATE_PROPERTY, modificationDate);
        }
        return mIndexer.index(new Note("note", properties));
    }

    private void assertIndex(List<Index> indexes, String name, long expected) {
        for (Index index : indexes) {
            if (index.getName().equals(name)) {
                assertEquals(Long.valueOf(expected), index.getValue());
                return;
            }
        }
        fail("Missing index: " + name);
    }
}
