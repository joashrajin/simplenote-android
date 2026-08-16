package com.automattic.simplenote

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.os.Bundle
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A resize must only patch the title/content visibility. A full updateAppWidget with the freshly
 * inflated layout would replace the views onUpdate installed, dropping the note text and the
 * widget's click intent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NoteWidgetOptionsChangedTest {
    private val appWidgetManager: AppWidgetManager = mock()

    @Test
    fun lightWidgetResizeIsPartialUpdate() {
        assertResizeIsPartialUpdate(NoteWidgetLight(), maxWidth = 200)
    }

    @Test
    fun darkWidgetResizeIsPartialUpdate() {
        assertResizeIsPartialUpdate(NoteWidgetDark(), maxWidth = 201)
    }

    private fun assertResizeIsPartialUpdate(provider: AppWidgetProvider, maxWidth: Int) {
        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth) }

        provider.onAppWidgetOptionsChanged(
            ApplicationProvider.getApplicationContext(),
            appWidgetManager,
            WIDGET_ID,
            options,
        )

        verify(appWidgetManager).partiallyUpdateAppWidget(eq(WIDGET_ID), any<RemoteViews>())
        verify(appWidgetManager, never()).updateAppWidget(any<Int>(), anyOrNull<RemoteViews>())
        verifyNoMoreInteractions(appWidgetManager)
    }

    private companion object {
        const val WIDGET_ID = 42
    }
}
