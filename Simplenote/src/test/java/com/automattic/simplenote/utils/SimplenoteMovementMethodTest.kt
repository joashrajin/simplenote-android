package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.MotionEvent
import android.view.View.MeasureSpec
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.widgets.CheckableSpan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins checkbox taps in the editor. Native graphics gives the text real glyph widths, and touch
 * coordinates are derived from the laid-out text so the tests target offsets rather than metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimplenoteMovementMethodTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val method = SimplenoteMovementMethod.getInstance()

    private val firstCheckbox = CheckableSpan()
    private val secondCheckbox = CheckableSpan()

    private class FocusableTextView(context: Context) : TextView(context) {
        var focused = false

        override fun hasFocus() = focused
    }

    private fun textView(configure: TextView.() -> Unit = {}): FocusableTextView {
        val text = SpannableStringBuilder("☐ first\n☐ second").apply {
            setSpan(firstCheckbox, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(secondCheckbox, SECOND_LINE_START, SECOND_LINE_START + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return FocusableTextView(context).apply {
            setText(text, TextView.BufferType.SPANNABLE)
            configure()
            measure(
                MeasureSpec.makeMeasureSpec(SIZE, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(SIZE, MeasureSpec.EXACTLY)
            )
            layout(0, 0, SIZE, SIZE)
        }
    }

    private fun touch(view: TextView, action: Int, offset: Int): Boolean {
        val layout = view.layout
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset) + view.totalPaddingLeft - view.scrollX
        val y = layout.getLineTop(line) + 1f + view.totalPaddingTop - view.scrollY
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        return try {
            method.onTouchEvent(view, view.text as Spannable, event)
        } finally {
            event.recycle()
        }
    }

    @Test
    fun getInstanceReturnsOneSharedInstance() {
        assertSame(SimplenoteMovementMethod.getInstance(), SimplenoteMovementMethod.getInstance())
    }

    @Test
    fun downOnACheckboxConsumesTheEventAndBlocksFocusOnAnUnfocusedView() {
        val view = textView()

        assertTrue(touch(view, MotionEvent.ACTION_DOWN, 0))

        assertFalse(view.isFocusableInTouchMode)
        assertFalse(firstCheckbox.isChecked)
    }

    @Test
    fun downOnACheckboxLeavesAFocusedViewFocusable() {
        val view = textView { isFocusableInTouchMode = true }
        view.focused = true

        assertTrue(touch(view, MotionEvent.ACTION_DOWN, 0))

        assertTrue(view.isFocusableInTouchMode)
        assertFalse(firstCheckbox.isChecked)
    }

    @Test
    fun upOnACheckboxTogglesItAndRestoresFocusability() {
        val view = textView()

        touch(view, MotionEvent.ACTION_DOWN, 0)
        assertTrue(touch(view, MotionEvent.ACTION_UP, 0))

        assertTrue(firstCheckbox.isChecked)
        assertFalse(secondCheckbox.isChecked)
        assertTrue(view.isFocusableInTouchMode)

        assertTrue(touch(view, MotionEvent.ACTION_UP, 0))
        assertFalse(firstCheckbox.isChecked)
    }

    @Test
    fun upOnTheSpaceAfterACheckboxTogglesIt() {
        val view = textView()

        assertTrue(touch(view, MotionEvent.ACTION_UP, 1))
        assertTrue(touch(view, MotionEvent.ACTION_UP, SECOND_LINE_START + 1))

        assertTrue(firstCheckbox.isChecked)
        assertTrue(secondCheckbox.isChecked)
    }

    @Test
    fun touchesOnTextAreNotConsumed() {
        val view = textView { isFocusableInTouchMode = true }

        assertFalse(touch(view, MotionEvent.ACTION_DOWN, 4))
        assertFalse(touch(view, MotionEvent.ACTION_UP, 4))
        assertFalse(touch(view, MotionEvent.ACTION_UP, SECOND_LINE_START + 4))

        assertFalse(firstCheckbox.isChecked)
        assertFalse(secondCheckbox.isChecked)
        assertTrue(view.isFocusableInTouchMode)
    }

    @Test
    fun otherActionsOnACheckboxAreConsumedWithoutEffect() {
        val view = textView { isFocusableInTouchMode = true }

        assertTrue(touch(view, MotionEvent.ACTION_MOVE, 0))
        assertTrue(touch(view, MotionEvent.ACTION_CANCEL, 0))

        assertFalse(firstCheckbox.isChecked)
        assertTrue(view.isFocusableInTouchMode)
    }

    @Test
    fun touchesAccountForPaddingAndScroll() {
        val view = textView { setPadding(120, 80, 0, 0) }
        view.scrollTo(0, view.layout.getLineTop(1))

        assertTrue(touch(view, MotionEvent.ACTION_UP, SECOND_LINE_START))
        assertFalse(touch(view, MotionEvent.ACTION_UP, SECOND_LINE_START + 4))

        assertFalse(firstCheckbox.isChecked)
        assertTrue(secondCheckbox.isChecked)
    }

    private companion object {
        const val SIZE = 1000
        const val SECOND_LINE_START = 8
    }
}
