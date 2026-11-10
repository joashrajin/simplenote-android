package com.automattic.simplenote;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.automattic.simplenote.utils.PrefUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class NoteListWidgetFactoryTest {
    // The factory reaches NotesRepository through an entry point (WidgetStreams), which resolves
    // against the TestDataModule buckets on HiltTestApplication.
    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Test
    public void getViewAtBeforeDataSetChangedReturnsEmptyRow() {
        Context context = ApplicationProvider.getApplicationContext();
        NoteListWidgetFactory factory = new NoteListWidgetFactory(context, new Intent());

        assertEquals(0, factory.getCount());

        RemoteViews views = factory.getViewAt(0);

        assertNotNull(views);
        assertEquals(context.getPackageName(), views.getPackage());
        assertEquals(PrefUtils.getLayoutWidgetListItem(context, true), views.getLayoutId());
    }

    @Test
    public void getViewAtAfterDestroyReturnsEmptyRow() {
        Context context = ApplicationProvider.getApplicationContext();
        NoteListWidgetFactory factory = new NoteListWidgetFactory(context, new Intent());
        factory.onDataSetChanged();
        factory.onDestroy();

        assertEquals(0, factory.getCount());

        RemoteViews views = factory.getViewAt(0);

        assertNotNull(views);
        assertEquals(context.getPackageName(), views.getPackage());
        assertEquals(PrefUtils.getLayoutWidgetListItem(context, true), views.getLayoutId());
    }

    @Test
    public void positionItemIdsAreReportedAsUnstable() {
        Context context = ApplicationProvider.getApplicationContext();
        NoteListWidgetFactory factory = new NoteListWidgetFactory(context, new Intent());

        assertEquals(0L, factory.getItemId(0));
        assertEquals(7L, factory.getItemId(7));
        assertFalse(factory.hasStableIds());
    }
}
