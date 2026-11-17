package com.automattic.simplenote

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/**
 * Signed out, the configure activities must cancel placement and finish without querying notes
 * or showing the picker dialog on a finishing activity. ScreenshotDataModule supplies the
 * signed-out Simperium and a notes repository whose search yields an (empty) note list, so
 * falling through to the picker would show the dialog.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class NoteWidgetConfigureActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun darkConfigureFinishesWithoutDialogWhenSignedOut() {
        assertSignedOutFinishesWithoutDialog(NoteWidgetDarkConfigureActivity::class.java)
    }

    @Test
    fun lightConfigureFinishesWithoutDialogWhenSignedOut() {
        assertSignedOutFinishesWithoutDialog(NoteWidgetLightConfigureActivity::class.java)
    }

    private fun <A : Activity> assertSignedOutFinishesWithoutDialog(activityClass: Class<A>) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, activityClass).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID)

        val activity = Robolectric.buildActivity(activityClass, intent).create().get()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertEquals(context.getString(R.string.log_in_add_widget), ShadowToast.getTextOfLatestToast())
        assertNull(ShadowDialog.getLatestDialog())
    }

    private companion object {
        const val WIDGET_ID = 42
    }
}
