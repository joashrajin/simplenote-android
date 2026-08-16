package com.automattic.simplenote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NoteWidgetOptionsChangedTest {
    private static final int WIDGET_ID = 42;

    @Test
    public void lightWidgetResizeUsesPartialUpdate() {
        verifyPartialResizeUpdate(new NoteWidgetLight(), 200);
    }

    @Test
    public void darkWidgetResizeUsesPartialUpdate() {
        verifyPartialResizeUpdate(new NoteWidgetDark(), 201);
    }

    private void verifyPartialResizeUpdate(AppWidgetProvider provider, int maxWidth) {
        Context context = ApplicationProvider.getApplicationContext();
        AppWidgetManager appWidgetManager = mock(AppWidgetManager.class);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth);

        provider.onAppWidgetOptionsChanged(context, appWidgetManager, WIDGET_ID, options);

        verify(appWidgetManager).partiallyUpdateAppWidget(eq(WIDGET_ID), any(RemoteViews.class));
        verify(appWidgetManager, never()).updateAppWidget(eq(WIDGET_ID), any(RemoteViews.class));
    }
}
