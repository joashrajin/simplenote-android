package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TagsMultiAutoCompleteTextViewTest {
    @Test
    fun enterSequenceNotifiesOnceAfterInputIsCleared() {
        val input = createInput("tag")

        assertTrue(input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)))
        assertTrue(input.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 1)))
        assertTrue(input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)))

        assertEquals(listOf("tag"), input.notifications)
    }

    private fun createInput(text: String): RecordingTagInput {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return RecordingTagInput(ContextThemeWrapper(context, R.style.Theme_Simplestyle)).apply {
            setText(text)
        }
    }

    private class RecordingTagInput(context: Context) : TagsMultiAutoCompleteTextView(context) {
        val notifications = mutableListOf<String>()

        override fun notifyTagsChanged() {
            notifications += text.toString().trim()
            setText("")
        }
    }
}
