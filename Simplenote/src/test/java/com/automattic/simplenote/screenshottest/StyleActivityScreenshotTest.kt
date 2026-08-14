package com.automattic.simplenote.screenshottest

import com.automattic.simplenote.StyleActivity
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
 * Goldens for the style picker. The screen renders a pre-baked card for every premium style plus
 * the themed toolbar and window chrome, which makes it a dense pin for theme-parent changes.
 * Matrix: {light, dark} x {Default, Black}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class StyleActivityScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun lightDefault() = capture(AppStyle.DEFAULT, darkMode = false, goldenName = "style_activity_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkDefault() = capture(AppStyle.DEFAULT, darkMode = true, goldenName = "style_activity_dark_default")

    @Test
    fun lightBlack() = capture(AppStyle.BLACK, darkMode = false, goldenName = "style_activity_light_black")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkBlack() = capture(AppStyle.BLACK, darkMode = true, goldenName = "style_activity_dark_black")

    private fun capture(style: AppStyle, darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(StyleActivity::class.java, style, darkMode).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }
}
