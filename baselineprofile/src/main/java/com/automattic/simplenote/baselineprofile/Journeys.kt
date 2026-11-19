package com.automattic.simplenote.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

// The release application ID: the benchmark and nonMinified build types add no suffix.
internal const val TARGET_PACKAGE = "com.automattic.simplenote"

private const val UI_TIMEOUT_MS = 10_000L
private const val POLL_INTERVAL_MS = 100L

internal enum class LaunchDestination { NOTE_LIST, LANDING }

/**
 * A fresh install has no account, so NotesActivity hands over to the Simperium landing screen in
 * onResume; a device signed in by hand lands on the note list instead.
 */
internal fun MacrobenchmarkScope.waitForLaunchDestination(): LaunchDestination {
    val noteList = By.res(packageName, "list_root")
    val landing = By.res(packageName, "button_signup")
    val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
    while (SystemClock.uptimeMillis() < deadline) {
        when {
            device.hasObject(noteList) -> return LaunchDestination.NOTE_LIST
            device.hasObject(landing) -> return LaunchDestination.LANDING
        }
        SystemClock.sleep(POLL_INTERVAL_MS)
    }
    error("Neither the note list nor the landing screen appeared within $UI_TIMEOUT_MS ms")
}

internal fun MacrobenchmarkScope.openFirstNoteAndGoBack() {
    val firstNote = device.findObject(By.res(packageName, "note_title")) ?: return
    firstNote.click()
    device.wait(Until.hasObject(By.res(packageName, "note_editor")), UI_TIMEOUT_MS)
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.hasObject(By.res(packageName, "list_root")), UI_TIMEOUT_MS)
}

/** Log in finishes the landing screen, so only Sign up returns to it on back. */
internal fun MacrobenchmarkScope.openSignupAndGoBack() {
    device.findObject(By.res(packageName, "button_signup")).click()
    device.wait(Until.hasObject(By.res(packageName, "input_email")), UI_TIMEOUT_MS)
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.hasObject(By.res(packageName, "button_signup")), UI_TIMEOUT_MS)
}
