package com.automattic.simplenote.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_BUTTON_TAPPED
import com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_SIGN_IN_TAPPED
import com.automattic.simplenote.analytics.AnalyticsTracker.Stat.NOTE_LIST_WIDGET_TAPPED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class WidgetUtilsTest {
    @Test
    fun noteListWidgetPendingIntentIdentityIncludesControlAndWidget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appWidgetId = Int.MIN_VALUE
        var layoutTapped: PendingIntent? = null
        var layoutSignIn: PendingIntent? = null
        var buttonTapped: PendingIntent? = null
        var buttonNeutral: PendingIntent? = null
        var otherWidgetLayout: PendingIntent? = null

        try {
            layoutTapped = WidgetUtils.getNoteListWidgetLayoutPendingIntent(
                context,
                appWidgetId,
                NOTE_LIST_WIDGET_TAPPED
            )
            layoutSignIn = WidgetUtils.getNoteListWidgetLayoutPendingIntent(
                context,
                appWidgetId,
                NOTE_LIST_WIDGET_SIGN_IN_TAPPED
            )
            buttonTapped = WidgetUtils.getNoteListWidgetButtonPendingIntent(
                context,
                appWidgetId,
                NOTE_LIST_WIDGET_BUTTON_TAPPED
            )
            buttonNeutral = WidgetUtils.getNoteListWidgetButtonPendingIntent(context, appWidgetId, null)
            otherWidgetLayout = WidgetUtils.getNoteListWidgetLayoutPendingIntent(
                context,
                appWidgetId + 1,
                NOTE_LIST_WIDGET_TAPPED
            )

            assertEquals(layoutTapped, layoutSignIn)
            assertEquals(buttonTapped, buttonNeutral)
            assertNotEquals(layoutTapped, buttonTapped)
            assertNotEquals(layoutTapped, otherWidgetLayout)
        } finally {
            cancel(layoutTapped)
            cancel(layoutSignIn)
            cancel(buttonTapped)
            cancel(buttonNeutral)
            cancel(otherWidgetLayout)
        }
    }

    @Test
    fun noteListWidgetIntentsCarryComponentActionFlagsAndClickExtra() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val layout = WidgetUtils.buildNoteListWidgetLayoutIntent(context, NOTE_LIST_WIDGET_TAPPED)
        val button = WidgetUtils.buildNoteListWidgetButtonIntent(context, NOTE_LIST_WIDGET_BUTTON_TAPPED)
        val buttonWithoutClick = WidgetUtils.buildNoteListWidgetButtonIntent(context, null)

        assertEquals(context.packageName + ".action.NOTE_LIST_WIDGET_LAYOUT", layout.action)
        assertEquals(context.packageName + ".action.NOTE_LIST_WIDGET_BUTTON", button.action)

        for (intent in arrayOf(layout, button, buttonWithoutClick)) {
            assertNotNull(intent.component)
            assertEquals(context.packageName, intent.component!!.packageName)
            assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                intent.flags
            )
        }

        assertEquals(NOTE_LIST_WIDGET_TAPPED, layout.getSerializableExtra(WidgetUtils.KEY_LIST_WIDGET_CLICK))
        assertEquals(NOTE_LIST_WIDGET_BUTTON_TAPPED, button.getSerializableExtra(WidgetUtils.KEY_LIST_WIDGET_CLICK))
        assertFalse(buttonWithoutClick.hasExtra(WidgetUtils.KEY_LIST_WIDGET_CLICK))
    }

    private fun cancel(pendingIntent: PendingIntent?) {
        pendingIntent?.cancel()
    }
}
