package com.automattic.simplenote

import android.content.Intent
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.widgets.SimplenoteEditText
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val NAVIGATION_MODE_GESTURAL = "2"
private const val POLL_ATTEMPTS = 50
private const val POLL_INTERVAL_MILLIS = 100L
private const val SETTLE_MILLIS = 1500L
private const val FIND_TIMEOUT_MILLIS = 5000L
private const val SWIPE_STEPS = 30
private const val RECREATION_FONT_SCALE = "1.3"

/**
 * Pins the NoteEditorFragment wiring of [NoteEditorKeyboardState] on a real IME: the inset hook in
 * trackKeyboardVisibility(), the onResume() check and the STATE_KEYBOARD_DISMISSED round trip.
 *
 * NoteEditorFragment casts its application to [Simplenote], so this needs the production
 * application and is skipped under the default Hilt runner. Run it with the production runner:
 *
 * ```
 * ./gradlew :Simplenote:assembleDebug :Simplenote:assembleDebugAndroidTest -PscreenshotInstrumentation
 * adb install -r -t Simplenote/build/outputs/apk/debug/Simplenote-debug.apk
 * adb install -r -t Simplenote/build/outputs/apk/androidTest/debug/Simplenote-debug-androidTest.apk
 * adb shell am instrument -w -e class com.automattic.simplenote.NoteEditorKeyboardDismissalTest \
 *     com.automattic.simplenote.debug.test/com.automattic.simplenote.ScreenshotAppRunner
 * ```
 *
 * The dismissal cases only fail without the fix on API 36+, where predictive back lets the IME
 * consume back and the content keeps its focus.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NoteEditorKeyboardDismissalTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val contentRes = "${instrumentation.targetContext.packageName}:id/note_content"
    private lateinit var scenario: ActivityScenario<NoteEditorActivity>
    private var note: Note? = null

    @Before
    fun launchEditor() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assumeTrue("Needs the production Simplenote application (ScreenshotAppRunner)", app is Simplenote)
        device.setOrientationNatural()

        val note = (app as Simplenote).notesBucket.newObject("keyboard-dismissal-" + System.nanoTime())
        note.content = "Keyboard dismissal\nSecond line of the note"
        note.save()
        this.note = note
        scenario = ActivityScenario.launch(
            Intent(app, NoteEditorActivity::class.java).putExtra(NoteEditorFragment.ARG_ITEM_ID, note.simperiumKey)
        )
        instrumentation.waitForIdleSync()
        SystemClock.sleep(SETTLE_MILLIS)
    }

    @After
    fun closeEditor() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        // A recreated editor is no longer the scenario's activity, so close it here.
        instrumentation.runOnMainSync {
            listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
                .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .filterIsInstance<NoteEditorActivity>()
                .forEach { it.finish() }
        }
        note?.delete()
        device.unfreezeRotation()
    }

    @Test
    fun backKeyDismissalKeepsKeyboardHiddenAcrossPauseAndResume() {
        showKeyboard()

        device.pressBack()

        assertDismissedAndStaysHiddenAcrossResume()
    }

    @Test
    fun edgeSwipeDismissalKeepsKeyboardHiddenAcrossPauseAndResume() {
        val navigationMode = device.executeShellCommand("settings get secure navigation_mode").trim()
        assumeTrue("Needs gesture navigation", navigationMode == NAVIGATION_MODE_GESTURAL)
        showKeyboard()

        device.swipe(1, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight / 3, SWIPE_STEPS)

        assertDismissedAndStaysHiddenAcrossResume()
    }

    @Test
    fun dismissalSurvivesActivityRecreation() {
        showKeyboard()
        device.pressBack()
        assertTrue("IME never hid", waitUntil { !isImeVisible() })
        val previous = resumedEditor()

        // ThemedAppCompatActivity.recreate() starts a fresh activity without saved state, and the
        // editor handles orientation changes itself, so a font scale change is what recreates it.
        val fontScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale $RECREATION_FONT_SCALE")
            assertTrue("editor not recreated", waitUntil { resumedEditor().let { it != null && it !== previous } })
            SystemClock.sleep(SETTLE_MILLIS)

            assertTrue("dismissal lost across recreation", editorFragment().isKeyboardDismissed())
            assertFalse("IME re-shown after recreation", isImeVisible())
        } finally {
            if (fontScale == "null") {
                device.executeShellCommand("settings delete system font_scale")
            } else {
                device.executeShellCommand("settings put system font_scale $fontScale")
            }
        }
    }

    @Test
    fun pauseAndResumeWhileTypingShowsKeyboardAgain() {
        showKeyboard()

        assertKeyboardReturnsAfterPauseAndResume()
    }

    @Test
    fun tappingTheContentAfterADismissalShowsKeyboardAgainOnResume() {
        showKeyboard()
        device.pressBack()
        assertTrue("IME never hid", waitUntil { !isImeVisible() })

        showKeyboard()

        assertKeyboardReturnsAfterPauseAndResume()
    }

    @Test
    fun rotationWhileTypingIsNotADismissal() {
        showKeyboard()

        device.setOrientationLeft()
        SystemClock.sleep(SETTLE_MILLIS)
        device.setOrientationNatural()
        SystemClock.sleep(SETTLE_MILLIS)

        assertTrue("IME hidden after rotating", isImeVisible())
        assertTrue("content lost focus after rotating", contentHasFocus())
        assertFalse("rotation counted as a dismissal", editorFragment().isKeyboardDismissed())
        assertKeyboardReturnsAfterPauseAndResume()
    }

    @Test
    fun switchingInputMethodsWhileTypingIsNotADismissal() {
        val current = device.executeShellCommand("settings get secure default_input_method").trim()
        val other = device.executeShellCommand("ime list -s -a").lines().map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != current }
        assumeTrue("Needs a second input method", other != null)
        device.executeShellCommand("ime enable $other")
        showKeyboard()

        // The assertions run while the other input method is up: switching back does not always bring
        // the keyboard back on the emulator, and a keyboard left hidden while editing is a dismissal.
        try {
            device.executeShellCommand("ime set $other")
            SystemClock.sleep(SETTLE_MILLIS)
            assertTrue("IME never came back after switching", waitUntil { isImeVisible() })

            assertTrue("content lost focus after switching input methods", contentHasFocus())
            assertFalse("input method switch counted as a dismissal", editorFragment().isKeyboardDismissed())
            assertKeyboardReturnsAfterPauseAndResume()
        } finally {
            device.executeShellCommand("ime set $current")
        }
    }

    private fun showKeyboard() {
        val content = checkNotNull(device.wait(Until.findObject(By.res(contentRes)), FIND_TIMEOUT_MILLIS)) {
            "editor content not found"
        }
        content.click()
        assertTrue("IME never showed", waitUntil { isImeVisible() })
        SystemClock.sleep(SETTLE_MILLIS / 2)
        assertTrue(contentHasFocus())
        assertFalse(editorFragment().isKeyboardDismissed())
    }

    private fun assertDismissedAndStaysHiddenAcrossResume() {
        assertTrue("IME never hid", waitUntil { !isImeVisible() })
        SystemClock.sleep(SETTLE_MILLIS)
        assertTrue("editor left the screen", scenario.state == Lifecycle.State.RESUMED)
        assertTrue("hide not recorded as a dismissal", editorFragment().isKeyboardDismissed())

        repeat(2) { round ->
            pauseAndResume()
            SystemClock.sleep(SETTLE_MILLIS)
            assertFalse("IME re-shown on resume $round", isImeVisible())
        }
    }

    private fun assertKeyboardReturnsAfterPauseAndResume() {
        pauseAndResume()
        assertTrue("IME did not come back on resume", waitUntil { isImeVisible() })
        assertTrue("content lost focus across pause and resume", contentHasFocus())
    }

    private fun pauseAndResume() {
        scenario.moveToState(Lifecycle.State.CREATED)
        SystemClock.sleep(SETTLE_MILLIS / 2)
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private fun resumedEditor(): NoteEditorActivity? {
        var editor: NoteEditorActivity? = null
        instrumentation.runOnMainSync {
            editor = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<NoteEditorActivity>().firstOrNull()
        }
        return editor
    }

    private fun <T> onEditor(block: (NoteEditorActivity) -> T): T {
        val editor = checkNotNull(resumedEditor()) { "no resumed editor" }
        var result: T? = null
        instrumentation.runOnMainSync { result = block(editor) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun editorFragment(): NoteEditorFragment = onEditor { activity ->
        activity.supportFragmentManager.fragments.filterIsInstance<NoteEditorFragment>().first()
    }

    private fun isImeVisible(): Boolean = onEditor { activity ->
        ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun contentHasFocus(): Boolean = onEditor { activity ->
        activity.findViewById<SimplenoteEditText>(R.id.note_content).hasFocus()
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        repeat(POLL_ATTEMPTS) {
            if (condition()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }
}
