package com.automattic.simplenote.screenshottest

import android.content.Context
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.automattic.simplenote.AddTagActivity
import com.automattic.simplenote.viewmodels.AddTagViewModel
import com.automattic.simplenote.widgets.MorphCircleToRectangle
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Lifecycle behavior and goldens for the add-tag faux-dialog activity: the only always-on
 * Widget.MaterialComponents.TextInputLayout.OutlinedBox surface plus the Theme.Transparent.Dialog
 * family. The goldens pin both the text-field styling and the translucent dialog chrome. The activity's
 * manifest theme is fixed to Theme.Transparent.Dialog (the Default style family) regardless of the
 * premium style preference, so the matrix is {light, dark} only.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class AddTagActivityScreenshotTest {
    private val keyboardDelay = Duration.ofMillis(MorphCircleToRectangle.DURATION.toLong() + 1)

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun light() = capture(darkMode = false, goldenName = "add_tag_activity_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun dark() = capture(darkMode = true, goldenName = "add_tag_activity_dark_default")

    @Test
    fun pausedActivityIgnoresPendingAndLateKeyboardRequests() {
        ScreenshotHarness.applyStyle(AppStyle.DEFAULT, darkMode = false)
        ActivityScenario.launch(AddTagActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            val inputMethodManager = inputMethodManager(scenario)
            assertFalse(shadowOf(inputMethodManager).isSoftInputVisible)

            scenario.moveToState(Lifecycle.State.STARTED)
            shadowOf(Looper.getMainLooper()).idleFor(keyboardDelay)
            assertFalse(shadowOf(inputMethodManager).isSoftInputVisible)

            scenario.onActivity { activity ->
                ViewModelProvider(activity)[AddTagViewModel::class.java].start()
            }
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(Looper.getMainLooper()).idleFor(keyboardDelay)

            assertFalse(shadowOf(inputMethodManager).isSoftInputVisible)
        }
    }

    @Test
    fun resumedActivityShowsKeyboardAfterStartupDelay() {
        ScreenshotHarness.applyStyle(AppStyle.DEFAULT, darkMode = false)
        ActivityScenario.launch(AddTagActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            val inputMethodManager = inputMethodManager(scenario)

            shadowOf(Looper.getMainLooper()).idleFor(keyboardDelay)

            assertTrue(shadowOf(inputMethodManager).isSoftInputVisible)
        }
    }

    private fun capture(darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(AddTagActivity::class.java, AppStyle.DEFAULT, darkMode).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }

    private fun inputMethodManager(scenario: ActivityScenario<AddTagActivity>): InputMethodManager {
        lateinit var inputMethodManager: InputMethodManager
        scenario.onActivity { activity ->
            inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        }
        return inputMethodManager
    }
}
