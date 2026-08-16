package com.automattic.simplenote;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.database.sqlite.SQLiteException;

import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.utils.PrefUtils;
import com.simperium.client.Bucket;
import com.simperium.client.Query;

import org.junit.Test;
import org.mockito.MockedStatic;

public class NoteListWidgetFactoryCursorTest {
    @Test
    @SuppressWarnings("unchecked")
    public void failedRefreshKeepsLastGoodCursorUntilLaterSuccess() {
        Simplenote application = mock(Simplenote.class);
        Intent intent = mock(Intent.class);
        Bucket<Note> notesBucket = mock(Bucket.class);
        Query<Note> query = mock(Query.class);
        Bucket.ObjectCursor<Note> initialCursor = mock(Bucket.ObjectCursor.class);
        Bucket.ObjectCursor<Note> failedCursor = mock(Bucket.ObjectCursor.class);
        Bucket.ObjectCursor<Note> replacementCursor = mock(Bucket.ObjectCursor.class);

        when(application.getApplicationContext()).thenReturn(application);
        when(application.getNotesBucket()).thenReturn(notesBucket);
        when(notesBucket.query()).thenReturn(query);
        when(query.where(Note.DELETED_PROPERTY, Query.ComparisonType.NOT_EQUAL_TO, true)).thenReturn(query);
        when(query.execute()).thenReturn(initialCursor, failedCursor, replacementCursor);
        when(initialCursor.getCount()).thenReturn(2);
        when(failedCursor.getCount()).thenThrow(new SQLiteException("Unable to query notes"));
        when(replacementCursor.getCount()).thenReturn(4);

        try (MockedStatic<PrefUtils> ignored = mockStatic(PrefUtils.class)) {
            NoteListWidgetFactory factory = new NoteListWidgetFactory(application, intent);

            factory.onDataSetChanged();
            assertEquals(2, factory.getCount());

            factory.onDataSetChanged();
            assertEquals(2, factory.getCount());
            verify(initialCursor, never()).close();
            verify(failedCursor).close();

            factory.onDataSetChanged();
            assertEquals(4, factory.getCount());
            verify(initialCursor).close();
            verify(replacementCursor, never()).close();

            factory.onDestroy();
            assertEquals(0, factory.getCount());
            verify(replacementCursor).close();
        }
    }
}
