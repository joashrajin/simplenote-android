package com.automattic.simplenote.screenshottest

import android.widget.TextView
import com.automattic.simplenote.AboutActivity
import com.automattic.simplenote.R
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
 * Goldens for the About screen, the baseline for its Compose rewrite. The activity's manifest theme
 * is fixed to Theme.Simplestyle.About (blue in both modes) regardless of the premium style
 * preference, so the matrix is {light, dark} only; both are recorded so the rewrite is pinned in
 * each mode.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class AboutActivityScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun light() = capture(darkMode = false, goldenName = "about_activity_light")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun dark() = capture(darkMode = true, goldenName = "about_activity_dark")

    // The phone-height captures cut off the California notice and the copyright line; these pin the whole screen.
    @Test
    @Config(qualifiers = TALL)
    fun lightFull() = capture(darkMode = false, goldenName = "about_activity_light_full")

    @Test
    @Config(qualifiers = TALL_NIGHT)
    fun darkFull() = capture(darkMode = true, goldenName = "about_activity_dark_full")

    private fun capture(darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(AboutActivity::class.java, AppStyle.DEFAULT, darkMode).use { scenario ->
            // The fragment renders BuildConfig's version name/code and git hash, and the current
            // year, all of which drift between commits; pin them to fixed values in the same formats.
            scenario.onActivity { activity ->
                activity.findViewById<TextView>(R.id.about_version).text = FIXED_VERSION
                activity.findViewById<TextView>(R.id.about_copyright).text =
                    activity.getString(R.string.about_copyright, FIXED_YEAR)
            }
            ScreenshotHarness.settle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }

    private companion object {
        const val FIXED_VERSION = "1.0 (1) debug (0000000)"
        const val FIXED_YEAR = 2026
        const val TALL = "w360dp-h1400dp-xhdpi"
        const val TALL_NIGHT = "w360dp-h1400dp-night-xhdpi"
    }
}
