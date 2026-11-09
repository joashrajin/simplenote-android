package com.automattic.simplenote.screenshottest

import com.automattic.simplenote.TagsActivity
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
 * Baseline goldens for the TagsActivity list screen. Proof matrix for the harness:
 * {light, dark} x {Default, Black}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class TagsActivityScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun lightDefault() = capture(AppStyle.DEFAULT, darkMode = false, goldenName = "tags_activity_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkDefault() = capture(AppStyle.DEFAULT, darkMode = true, goldenName = "tags_activity_dark_default")

    @Test
    fun lightBlack() = capture(AppStyle.BLACK, darkMode = false, goldenName = "tags_activity_light_black")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkBlack() = capture(AppStyle.BLACK, darkMode = true, goldenName = "tags_activity_dark_black")

    // Matrix and Sepia night record the accepted attr-retirement deltas: their top bars moved off
    // the per-style toolbarColor values onto colorSurfaceContainerLow, which the drawer already
    // owns (Matrix #000000 -> #121416, Sepia #2d2b29 -> #23211f).
    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkMatrix() = capture(AppStyle.MATRIX, darkMode = true, goldenName = "tags_activity_dark_matrix")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkSepia() = capture(AppStyle.SEPIA, darkMode = true, goldenName = "tags_activity_dark_sepia")

    private fun capture(style: AppStyle, darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(TagsActivity::class.java, style, darkMode).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }
}
