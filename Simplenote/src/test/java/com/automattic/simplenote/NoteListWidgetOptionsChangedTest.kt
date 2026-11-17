package com.automattic.simplenote

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.simperium.Simperium
import com.simperium.client.User
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A resize must only patch the button/padding views. A full updateAppWidget with the freshly
 * inflated layout would replace the views onUpdate installed, dropping the list's remote adapter,
 * its item click template, and the widget's click intents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NoteListWidgetOptionsChangedTest {
    private val appWidgetManager: AppWidgetManager = mock()
    private val context = SignedInContext(ApplicationProvider.getApplicationContext())
    private val resizedOptions = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 400)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400)
    }

    @Test
    fun darkListWidgetResizeIsPartialUpdate() {
        assertResizeIsPartialUpdate(NoteListWidgetDark())
    }

    @Test
    fun lightListWidgetResizeIsPartialUpdate() {
        assertResizeIsPartialUpdate(NoteListWidgetLight())
    }

    private fun assertResizeIsPartialUpdate(provider: AppWidgetProvider) {
        provider.onAppWidgetOptionsChanged(context, appWidgetManager, WIDGET_ID, resizedOptions)

        verify(appWidgetManager).partiallyUpdateAppWidget(eq(WIDGET_ID), any<RemoteViews>())
        verify(appWidgetManager, never()).updateAppWidget(any<Int>(), anyOrNull<RemoteViews>())
        verifyNoMoreInteractions(appWidgetManager)
    }

    /** The providers reach Simperium through the application context cast to [Simplenote]. */
    private class SignedInContext(base: Context) : ContextWrapper(base) {
        private val signedInUser = mock<User> { on { status } doReturn User.Status.AUTHORIZED }
        private val simperium = mock<Simperium> { on { user } doReturn signedInUser }
        private val app = mock<Simplenote> { on { getSimperium() } doReturn simperium }

        override fun getApplicationContext(): Context = app
    }

    private companion object {
        const val WIDGET_ID = 7
    }
}
