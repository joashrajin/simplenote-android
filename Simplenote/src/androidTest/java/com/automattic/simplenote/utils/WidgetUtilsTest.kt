package com.automattic.simplenote.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_BUTTON_TAPPED;
import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_SIGN_IN_TAPPED;
import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_TAPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class WidgetUtilsTest {
    @Test
    public void noteListWidgetPendingIntentIdentityIncludesControlAndWidget() {
        Context context = ApplicationProvider.getApplicationContext();
        int appWidgetId = Integer.MIN_VALUE;
        PendingIntent layoutTapped = null;
        PendingIntent layoutSignIn = null;
        PendingIntent buttonTapped = null;
        PendingIntent buttonNeutral = null;
        PendingIntent otherWidgetLayout = null;

        try {
            layoutTapped = WidgetUtils.getNoteListWidgetLayoutPendingIntent(
                context,
                appWidgetId,
                NOTE_LIST_WIDGET_TAPPED
            );
            layoutSignIn = WidgetUtils.getNoteListWidgetLayoutPendingIntent(
                context,
                appWidgetId,
                NOTE_LIST_WIDGET_SIGN_IN_TAPPED
            );
            buttonTapped = WidgetUtils.getNoteListWidgetButtonPendingIntent(
                context,
                appWidgetId,
                NOTE_LIST_WIDGET_BUTTON_TAPPED
            );
            buttonNeutral = WidgetUtils.getNoteListWidgetButtonPendingIntent(context, appWidgetId, null);
            otherWidgetLayout = WidgetUtils.getNoteListWidgetLayoutPendingIntent(
                context,
                appWidgetId + 1,
                NOTE_LIST_WIDGET_TAPPED
            );

            assertEquals(layoutTapped, layoutSignIn);
            assertEquals(buttonTapped, buttonNeutral);
            assertNotEquals(layoutTapped, buttonTapped);
            assertNotEquals(layoutTapped, otherWidgetLayout);
        } finally {
            cancel(layoutTapped);
            cancel(layoutSignIn);
            cancel(buttonTapped);
            cancel(buttonNeutral);
            cancel(otherWidgetLayout);
        }
    }

    @Test
    public void noteListWidgetIntentsCarryComponentActionFlagsAndClickExtra() {
        Context context = ApplicationProvider.getApplicationContext();

        Intent layout = WidgetUtils.buildNoteListWidgetLayoutIntent(context, NOTE_LIST_WIDGET_TAPPED);
        Intent button = WidgetUtils.buildNoteListWidgetButtonIntent(context, NOTE_LIST_WIDGET_BUTTON_TAPPED);
        Intent buttonWithoutClick = WidgetUtils.buildNoteListWidgetButtonIntent(context, null);

        assertEquals(context.getPackageName() + ".action.NOTE_LIST_WIDGET_LAYOUT", layout.getAction());
        assertEquals(context.getPackageName() + ".action.NOTE_LIST_WIDGET_BUTTON", button.getAction());

        for (Intent intent : new Intent[]{layout, button, buttonWithoutClick}) {
            assertNotNull(intent.getComponent());
            assertEquals(context.getPackageName(), intent.getComponent().getPackageName());
            assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP,
                intent.getFlags()
            );
        }

        assertEquals(NOTE_LIST_WIDGET_TAPPED, layout.getSerializableExtra(WidgetUtils.KEY_LIST_WIDGET_CLICK));
        assertEquals(NOTE_LIST_WIDGET_BUTTON_TAPPED, button.getSerializableExtra(WidgetUtils.KEY_LIST_WIDGET_CLICK));
        assertFalse(buttonWithoutClick.hasExtra(WidgetUtils.KEY_LIST_WIDGET_CLICK));
    }

    private static void cancel(PendingIntent pendingIntent) {
        if (pendingIntent != null) {
            pendingIntent.cancel();
        }
    }
}
