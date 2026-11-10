package com.automattic.simplenote.screenshottest

import androidx.compose.ui.platform.ComposeView
import com.automattic.simplenote.StyleActivity
import com.automattic.simplenote.compose.ThemeSwatchScreen
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
 * Goldens for the SimplenoteTheme bridge: the debug ThemeSwatchScreen is composed inside a themed
 * activity (StyleActivity, launched by the harness so ThemeUtils.getStyle applies the persisted
 * style), which is exactly how production Compose screens will host the bridge. Each swatch pins
 * one resolved ColorScheme role or surviving custom attr, and the header line pins the
 * styleFontFamily-driven typography. Matrix per the Phase 3 design: {light, dark} x
 * {Default, Black}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class ThemeSwatchScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun lightDefault() = capture(AppStyle.DEFAULT, darkMode = false, goldenName = "theme_swatch_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkDefault() = capture(AppStyle.DEFAULT, darkMode = true, goldenName = "theme_swatch_dark_default")

    @Test
    fun lightBlack() = capture(AppStyle.BLACK, darkMode = false, goldenName = "theme_swatch_light_black")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkBlack() = capture(AppStyle.BLACK, darkMode = true, goldenName = "theme_swatch_dark_black")

    private fun capture(style: AppStyle, darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(StyleActivity::class.java, style, darkMode).use { scenario ->
            lateinit var composeView: ComposeView
            scenario.onActivity { activity ->
                composeView = ComposeView(activity).apply { setContent { ThemeSwatchScreen() } }
                activity.setContentView(composeView)
            }
            ScreenshotHarness.settle()
            scenario.onActivity {
                composeView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }
}
