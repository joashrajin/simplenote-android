package com.automattic.simplenote

import com.automattic.simplenote.NoteEditorFragment.shouldDropContentFocus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * On Android 16+ predictive back lets the IME consume back, so SimplenoteEditText.onKeyPreIme()
 * never clears the content focus and onResume() re-shows the keyboard. The editor drops the focus
 * from its IME inset listener instead; these cases pin when that is allowed.
 */
class NoteEditorFragmentFocusTest {
    @Test
    fun `keyboard dismissed while resumed and window focused drops focus`() {
        assertTrue(dropFocus())
    }

    @Test
    fun `keyboard hidden because the fragment paused keeps focus`() {
        assertFalse(dropFocus(isResumed = false))
    }

    @Test
    fun `keyboard hidden because a dialog took window focus keeps focus`() {
        assertFalse(dropFocus(hasWindowFocus = false))
    }

    @Test
    fun `keyboard hidden while the content field is not focused is ignored`() {
        assertFalse(dropFocus(hasFocus = false))
    }

    @Test
    fun `keyboard shown keeps focus`() {
        assertFalse(dropFocus(wasKeyboardVisible = false, isKeyboardVisible = true))
    }

    @Test
    fun `keyboard staying hidden keeps focus`() {
        assertFalse(dropFocus(wasKeyboardVisible = false, isKeyboardVisible = false))
    }

    private fun dropFocus(
        wasKeyboardVisible: Boolean = true,
        isKeyboardVisible: Boolean = false,
        isResumed: Boolean = true,
        hasFocus: Boolean = true,
        hasWindowFocus: Boolean = true,
    ) = shouldDropContentFocus(wasKeyboardVisible, isKeyboardVisible, isResumed, hasFocus, hasWindowFocus)
}
