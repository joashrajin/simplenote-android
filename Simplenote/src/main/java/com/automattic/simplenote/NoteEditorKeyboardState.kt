package com.automattic.simplenote

/**
 * Decides whether the note editor should show the keyboard again when it resumes.
 *
 * Content focus alone used to decide it, but on Android 16+ predictive back lets the IME consume
 * back, so SimplenoteEditText.onKeyPreIme() never runs and the content keeps focus after the user
 * dismissed the keyboard. The same happens on every version when the keyboard is collapsed from
 * its own hide button. The focus is left alone and the dismissal is remembered instead.
 *
 * A hide counts as a dismissal only while the editor is resumed and its window has focus: the
 * editor hides the keyboard itself in onPause(), and a dialog or popup taking the window focus
 * hides it too. Rotation and input method switches hide the keyboard briefly while resumed and
 * focused, then show it again, and showing it clears the dismissal. Until it shows again (about
 * 800 ms after a rotation on API 37) the hide reads as a dismissal, so leaving the editor inside
 * that window keeps the keyboard down on return, which is what the old behaviour did anyway.
 */
class NoteEditorKeyboardState(isDismissed: Boolean = false) {
    var isDismissed: Boolean = isDismissed
        private set

    fun onKeyboardVisibilityChanged(isVisible: Boolean, isResumed: Boolean, hasWindowFocus: Boolean) {
        if (isVisible) {
            isDismissed = false
        } else if (isResumed && hasWindowFocus) {
            isDismissed = true
        }
    }

    fun shouldShowKeyboardOnResume(contentHasFocus: Boolean): Boolean = contentHasFocus && !isDismissed
}
