package com.automattic.simplenote.utils;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.automattic.simplenote.NoteListWidgetDark;
import com.automattic.simplenote.NoteListWidgetLight;
import com.automattic.simplenote.NoteWidgetDark;
import com.automattic.simplenote.NoteWidgetLight;
import com.automattic.simplenote.analytics.AnalyticsTracker.Stat;

public class WidgetUtils {
    public static final String KEY_LIST_WIDGET_CLICK = "key_list_widget_click";
    public static final String KEY_WIDGET_CLICK = "key_widget_click";
    public static final int MINIMUM_HEIGHT_FOR_BUTTON = 150;
    public static final int MINIMUM_WIDTH_FOR_BUTTON = 300;
    private static final String ACTION_NOTE_LIST_WIDGET_LAYOUT = ".action.NOTE_LIST_WIDGET_LAYOUT";
    private static final String ACTION_NOTE_LIST_WIDGET_BUTTON = ".action.NOTE_LIST_WIDGET_BUTTON";

    public static PendingIntent getNoteListWidgetLayoutPendingIntent(
        Context context,
        int appWidgetId,
        @NonNull Stat click
    ) {
        return getNoteListWidgetPendingIntent(context, appWidgetId, buildNoteListWidgetLayoutIntent(context, click));
    }

    public static PendingIntent getNoteListWidgetButtonPendingIntent(
        Context context,
        int appWidgetId,
        @Nullable Stat click
    ) {
        return getNoteListWidgetPendingIntent(context, appWidgetId, buildNoteListWidgetButtonIntent(context, click));
    }

    @VisibleForTesting
    static Intent buildNoteListWidgetLayoutIntent(Context context, @NonNull Stat click) {
        return buildNoteListWidgetIntent(context, ACTION_NOTE_LIST_WIDGET_LAYOUT, click);
    }

    @VisibleForTesting
    static Intent buildNoteListWidgetButtonIntent(Context context, @Nullable Stat click) {
        return buildNoteListWidgetIntent(context, ACTION_NOTE_LIST_WIDGET_BUTTON, click);
    }

    private static Intent buildNoteListWidgetIntent(Context context, String action, @Nullable Stat click) {
        Intent intent = IntentUtils.maybeAliasedIntent(context);
        intent.setAction(context.getPackageName() + action);
        if (click != null) {
            intent.putExtra(KEY_LIST_WIDGET_CLICK, click);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private static PendingIntent getNoteListWidgetPendingIntent(Context context, int appWidgetId, Intent intent) {
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static void updateNoteWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateWidgetProvider(context, appWidgetManager, NoteWidgetDark.class, NoteWidgetDark.KEY_WIDGET_IDS_DARK);
        updateWidgetProvider(context, appWidgetManager, NoteWidgetLight.class, NoteWidgetLight.KEY_WIDGET_IDS_LIGHT);
        updateWidgetProvider(
            context,
            appWidgetManager,
            NoteListWidgetDark.class,
            NoteListWidgetDark.KEY_LIST_WIDGET_IDS_DARK
        );
        updateWidgetProvider(
            context,
            appWidgetManager,
            NoteListWidgetLight.class,
            NoteListWidgetLight.KEY_LIST_WIDGET_IDS_LIGHT
        );
    }

    private static void updateWidgetProvider(
        Context context,
        AppWidgetManager appWidgetManager,
        Class<?> provider,
        String widgetIdsKey
    ) {
        ComponentName component = new ComponentName(context, provider);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(component);
        Intent updateIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .setComponent(component)
            .putExtra(widgetIdsKey, widgetIds);
        context.sendBroadcast(updateIntent);
    }
}
