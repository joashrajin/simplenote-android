package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.automattic.simplenote.NoteListWidgetDark;
import com.automattic.simplenote.NoteListWidgetLight;
import com.automattic.simplenote.NoteWidgetDark;
import com.automattic.simplenote.NoteWidgetLight;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class WidgetUtilsBroadcastTest {
    @Test
    public void updateNoteWidgetsTargetsEachProvider() {
        Context application = ApplicationProvider.getApplicationContext();
        RecordingContext context = new RecordingContext(application);

        WidgetUtils.updateNoteWidgets(context);

        assertEquals(4, context.broadcasts.size());
        assertBroadcast(
            context,
            context.broadcasts.get(0),
            NoteWidgetDark.class,
            NoteWidgetDark.KEY_WIDGET_IDS_DARK
        );
        assertBroadcast(
            context,
            context.broadcasts.get(1),
            NoteWidgetLight.class,
            NoteWidgetLight.KEY_WIDGET_IDS_LIGHT
        );
        assertBroadcast(
            context,
            context.broadcasts.get(2),
            NoteListWidgetDark.class,
            NoteListWidgetDark.KEY_LIST_WIDGET_IDS_DARK
        );
        assertBroadcast(
            context,
            context.broadcasts.get(3),
            NoteListWidgetLight.class,
            NoteListWidgetLight.KEY_LIST_WIDGET_IDS_LIGHT
        );
    }

    private void assertBroadcast(
        Context context,
        Intent broadcast,
        Class<?> provider,
        String widgetIdsKey
    ) {
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_UPDATE, broadcast.getAction());
        assertEquals(new ComponentName(context, provider), broadcast.getComponent());
        assertEquals(Collections.singleton(widgetIdsKey), broadcast.getExtras().keySet());
        assertNotNull(broadcast.getIntArrayExtra(widgetIdsKey));
    }

    private static class RecordingContext extends ContextWrapper {
        private final List<Intent> broadcasts = new ArrayList<>();

        RecordingContext(Context base) {
            super(base);
        }

        @Override
        public void sendBroadcast(Intent intent) {
            broadcasts.add(new Intent(intent));
        }
    }
}
