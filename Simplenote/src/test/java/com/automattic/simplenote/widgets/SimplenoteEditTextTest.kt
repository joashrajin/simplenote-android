package com.automattic.simplenote.widgets

import android.app.Application
import android.content.Context
import android.text.Selection
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SimplenoteEditTextTest {
    @Test
    fun missingSelectionIsNotEnoughToFilter() {
        val editText = createEditText()
        editText.setText("[n")
        Selection.removeSelection(editText.text)

        assertEquals(-1, editText.selectionEnd)
        assertFalse(editText.enoughToFilter())
    }

    @Test
    fun openLinkTokenWithASelectionIsEnoughToFilter() {
        val editText = createEditText()
        editText.setText("[n")
        editText.setSelection(editText.length())

        assertTrue(editText.enoughToFilter())
    }

    private fun createEditText(): SimplenoteEditText {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return SimplenoteEditText(ContextThemeWrapper(context, R.style.Theme_Simplestyle))
    }
}
