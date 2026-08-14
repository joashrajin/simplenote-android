package com.automattic.simplenote;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.automattic.simplenote.utils.PrefUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NoteListWidgetFactoryTest {
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
        SimplenoteTest application = (SimplenoteTest) ApplicationProvider.getApplicationContext();
        application.setUseTestBucket(true);

        try {
            NoteListWidgetFactory factory = new NoteListWidgetFactory(application, new Intent());
            factory.onDataSetChanged();
            factory.onDestroy();

            assertEquals(0, factory.getCount());

            RemoteViews views = factory.getViewAt(0);

            assertNotNull(views);
            assertEquals(application.getPackageName(), views.getPackage());
            assertEquals(PrefUtils.getLayoutWidgetListItem(application, true), views.getLayoutId());
        } finally {
            application.setUseTestBucket(false);
        }
    }
}
