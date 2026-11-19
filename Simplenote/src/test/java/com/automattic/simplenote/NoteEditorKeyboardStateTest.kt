package com.automattic.simplenote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sequences below are the inset changes NoteEditorFragment sees on device (API 35 and 37,
 * gesture navigation). Each case ends with the question onResume() asks.
 */
class NoteEditorKeyboardStateTest {
    private val state = NoteEditorKeyboardState()

    @Test
    fun `keyboard dismissed by the user is not shown again on resume`() {
        state.shown()
        state.hidden()

        assertFalse(state.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    @Test
    fun `keyboard hidden by the editor pausing is shown again on resume`() {
        state.shown()
        state.hidden(isResumed = false)

        assertTrue(state.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    @Test
    fun `keyboard hidden by a dialog taking window focus is shown again on resume`() {
        state.shown()
        state.hidden(hasWindowFocus = false)

        assertTrue(state.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    @Test
    fun `rotation hides the keyboard briefly and it is still shown again on resume`() {
        state.shown()
        state.hidden()
        state.shown()

        assertTrue(state.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    @Test
    fun `switching input methods hides the keyboard briefly and it is still shown again on resume`() {
        state.shown()
        state.hidden()
        state.shown()
        state.hidden()
        state.shown()

        assertTrue(state.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    @Test
    fun `tapping the content after a dismissal shows the keyboard again on resume`() {
        state.shown()
        state.hidden()
        state.hidden(isResumed = false)
        state.shown()

        assertTrue(state.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    @Test
    fun `a dismissal survives the hide that pausing causes`() {
        state.shown()
        state.hidden()
        state.hidden(isResumed = false)

        assertFalse(state.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    @Test
    fun `keyboard is not shown on resume when the content has no focus`() {
        state.shown()

        assertFalse(state.shouldShowKeyboardOnResume(contentHasFocus = false))
    }

    @Test
    fun `a restored dismissal is not shown again on resume`() {
        val restored = NoteEditorKeyboardState(isDismissed = true)

        assertFalse(restored.shouldShowKeyboardOnResume(contentHasFocus = true))
    }

    private fun NoteEditorKeyboardState.shown() =
        onKeyboardVisibilityChanged(isVisible = true, isResumed = true, hasWindowFocus = true)

    private fun NoteEditorKeyboardState.hidden(isResumed: Boolean = true, hasWindowFocus: Boolean = true) =
        onKeyboardVisibilityChanged(isVisible = false, isResumed = isResumed, hasWindowFocus = hasWindowFocus)
}
