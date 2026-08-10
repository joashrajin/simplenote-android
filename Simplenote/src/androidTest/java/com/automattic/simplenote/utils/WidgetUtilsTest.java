package com.automattic.simplenote.utils;

import android.app.PendingIntent;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_BUTTON_TAPPED;
import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_SIGN_IN_TAPPED;
import static com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_TAPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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

    private static void cancel(PendingIntent pendingIntent) {
        if (pendingIntent != null) {
            pendingIntent.cancel();
        }
    }
}
