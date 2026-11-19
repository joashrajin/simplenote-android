package com.automattic.simplenote

import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import com.automattic.simplenote.screenshottest.AppStyle
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import com.automattic.simplenote.utils.ThemeUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins what the editor sheets inherit from [BottomSheetDialogBase]: a retained instance and, on API levels that
 * still honour bar colours (34 here), a sheet navigation bar painted in the sheet surface colour with icons that
 * follow the theme's lightness.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34])
class BottomSheetDialogBaseTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun sheetIsRetainedAcrossConfigurationChanges() {
        withShownSheet(darkMode = false) { sheet ->
            @Suppress("DEPRECATION")
            assertTrue(sheet.retainInstance)
        }
    }

    @Test
    fun lightSheetPaintsTheSurfaceColorBehindDarkNavigationIcons() {
        withShownSheet(darkMode = false) { sheet ->
            val dialog = sheet.dialog!!
            val window = dialog.window!!

            @Suppress("DEPRECATION")
            assertEquals(
                ThemeUtils.getColorFromAttribute(dialog.context, R.attr.colorSurfaceContainerLow),
                window.navigationBarColor
            )
            assertTrue(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars)
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun darkSheetPaintsTheSurfaceColorBehindLightNavigationIcons() {
        withShownSheet(darkMode = true) { sheet ->
            val dialog = sheet.dialog!!
            val window = dialog.window!!

            @Suppress("DEPRECATION")
            assertEquals(
                ThemeUtils.getColorFromAttribute(dialog.context, R.attr.colorSurfaceContainerLow),
                window.navigationBarColor
            )
            assertFalse(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars)
        }
    }

    private fun withShownSheet(darkMode: Boolean, assertions: (BottomSheetDialogBase) -> Unit) {
        val scenario: ActivityScenario<TagsActivity> =
            ScreenshotHarness.launchSettled(TagsActivity::class.java, AppStyle.DEFAULT, darkMode)
        scenario.use {
            lateinit var sheet: BottomSheetDialogBase
            it.onActivity { activity ->
                sheet = BottomSheetDialogBase()
                sheet.showNow(activity.supportFragmentManager, "base-sheet")
            }
            ScreenshotHarness.settle()
            assertions(sheet)
        }
    }
}
