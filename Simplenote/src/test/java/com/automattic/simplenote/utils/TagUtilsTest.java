package com.automattic.simplenote.utils;

import com.automattic.simplenote.models.Tag;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Query;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TagUtilsTest {
    private static final String NEW_TAG_NAME = "alpha";

    private Bucket<Tag> bucket;
    private Query<Tag> query;
    private Bucket.ObjectCursor<Tag> cursor;
    private Tag createdTag;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        bucket = mock(Bucket.class);
        query = mock(Query.class);
        cursor = mock(Bucket.ObjectCursor.class);
        createdTag = mock(Tag.class);

        when(bucket.getObject(TagUtils.hashTag(NEW_TAG_NAME))).thenThrow(new BucketObjectMissingException());
        when(bucket.query()).thenReturn(query);
        when(query.order(anyString(), any(Query.SortType.class))).thenReturn(query);
        when(query.limit(anyInt())).thenReturn(query);
        when(query.execute()).thenReturn(cursor);
        when(bucket.newObject(TagUtils.hashTag(NEW_TAG_NAME))).thenReturn(createdTag);
    }

    @Test
    public void createTagAfterDeletionUsesIndexAfterHighestPersistedTag() throws Exception {
        Tag survivingTag = mock(Tag.class);
        when(bucket.count()).thenReturn(2);
        when(cursor.moveToNext()).thenReturn(true);
        when(cursor.getObject()).thenReturn(survivingTag);
        when(survivingTag.getIndex()).thenReturn(2);

        TagUtils.createTagIfMissing(bucket, NEW_TAG_NAME);

        verify(createdTag).setIndex(3);
        verify(query).order("index", Query.SortType.DESCENDING);
        verify(query).limit(1);
        verify(cursor).close();
    }

    @Test
    public void createTagUsesCountWhenHighestPersistedTagHasNoIndex() throws Exception {
        Tag unindexedTag = mock(Tag.class);
        when(bucket.count()).thenReturn(3);
        when(cursor.moveToNext()).thenReturn(true);
        when(cursor.getObject()).thenReturn(unindexedTag);
        when(unindexedTag.getIndex()).thenReturn(null);

        TagUtils.createTagIfMissing(bucket, NEW_TAG_NAME);

        verify(createdTag).setIndex(3);
        verify(cursor).close();
    }

    @Test
    public void createTagUsesCountWhenItExceedsTheHighestIndex() throws Exception {
        Tag survivingTag = mock(Tag.class);
        when(bucket.count()).thenReturn(5);
        when(cursor.moveToNext()).thenReturn(true);
        when(cursor.getObject()).thenReturn(survivingTag);
        when(survivingTag.getIndex()).thenReturn(1);

        TagUtils.createTagIfMissing(bucket, NEW_TAG_NAME);

        verify(createdTag).setIndex(5);
        verify(cursor).close();
    }

    @Test
    public void createTagUsesCountWhenTheIndexQueryIsEmpty() throws Exception {
        when(bucket.count()).thenReturn(0);
        when(cursor.moveToNext()).thenReturn(false);

        TagUtils.createTagIfMissing(bucket, NEW_TAG_NAME);

        verify(createdTag).setIndex(0);
        verify(cursor).close();
    }

    @Test
    public void existingTagDoesNotQueryForANewIndex() throws Exception {
        doReturn(mock(Tag.class)).when(bucket).getObject(TagUtils.hashTag(NEW_TAG_NAME));

        TagUtils.createTagIfMissing(bucket, NEW_TAG_NAME);

        verify(bucket, never()).count();
        verify(bucket, never()).query();
        verify(bucket, never()).newObject(anyString());
    }
}
