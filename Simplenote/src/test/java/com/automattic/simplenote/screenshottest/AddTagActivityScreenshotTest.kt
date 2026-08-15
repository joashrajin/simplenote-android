package com.automattic.simplenote.screenshottest

import com.automattic.simplenote.AddTagActivity
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the add-tag faux-dialog activity: the only always-on
 * Widget.MaterialComponents.TextInputLayout.OutlinedBox surface plus the Theme.Transparent.Dialog
 * family, so it pins both the text-field styling and the translucent dialog chrome. The activity's
 * manifest theme is fixed to Theme.Transparent.Dialog (the Default style family) regardless of the
 * premium style preference, so the matrix is {light, dark} only.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class AddTagActivityScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun light() = capture(darkMode = false, goldenName = "add_tag_activity_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun dark() = capture(darkMode = true, goldenName = "add_tag_activity_dark_default")

    private fun capture(darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(AddTagActivity::class.java, AppStyle.DEFAULT, darkMode).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }
}
