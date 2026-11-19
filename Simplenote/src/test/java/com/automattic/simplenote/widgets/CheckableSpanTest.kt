package com.automattic.simplenote.widgets

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CheckableSpanTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun startsUncheckedAndStoresTheCheckedState() {
        val span = CheckableSpan()

        assertFalse(span.isChecked)
        span.isChecked = true
        assertTrue(span.isChecked)
        span.isChecked = false
        assertFalse(span.isChecked)
    }

    @Test
    fun isAClickableSpan() {
        assertTrue(CheckableSpan() is ClickableSpan)
    }

    @Test
    fun onClickTogglesTheStateForAPlainView() {
        val span = CheckableSpan()
        val view = View(context)

        span.onClick(view)
        assertTrue(span.isChecked)
        span.onClick(view)
        assertFalse(span.isChecked)
    }

    @Test
    fun onClickTogglesBeforeAskingTheEditorToRedrawTheCheckbox() {
        val span = CheckableSpan()
        var checkedWhenToggled: Boolean? = null
        val editText = mock<SimplenoteEditText> {
            on { toggleCheckbox(span) } doAnswer { checkedWhenToggled = span.isChecked }
        }

        span.onClick(editText)

        verify(editText).toggleCheckbox(span)
        assertEquals(true, checkedWhenToggled)
        assertTrue(span.isChecked)
    }

    @Test
    fun onClickSwallowsEditorFailuresAndKeepsTheNewState() {
        val span = CheckableSpan()
        val editText = mock<SimplenoteEditText> {
            on { toggleCheckbox(span) } doThrow IllegalStateException("boom")
        }

        span.onClick(editText)

        assertTrue(span.isChecked)
    }

    @Test
    fun equalityIsIdentityRegardlessOfState() {
        val first = CheckableSpan()
        val second = CheckableSpan()

        assertNotEquals(first, second)
        assertEquals(first, first)
        assertEquals(System.identityHashCode(first), first.hashCode())
    }

    @Test
    fun updateDrawStateKeepsTheClickableSpanLinkStyle() {
        val paint = TextPaint().apply {
            color = Color.BLACK
            linkColor = Color.MAGENTA
            isUnderlineText = false
        }

        CheckableSpan().updateDrawState(paint)

        assertEquals(Color.MAGENTA, paint.color)
        assertTrue(paint.isUnderlineText)
    }
}
