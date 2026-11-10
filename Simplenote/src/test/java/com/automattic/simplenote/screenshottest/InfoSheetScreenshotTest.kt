package com.automattic.simplenote.screenshottest

import androidx.fragment.app.Fragment
import com.automattic.simplenote.InfoBottomSheetDialog
import com.automattic.simplenote.TagsActivity
import com.automattic.simplenote.models.Note
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Acceptance record for the Material 3 bottom-sheet migration: the info sheet, shown over
 * TagsActivity, captures the M3 modal container (colorSurfaceContainerLow surface, 28dp top
 * corners) under the documented surface matrix. The matrix mirrors the dialog goldens'
 * {light, dark} x {Default, Black}: light Default/Black pin the unchanged white surface, dark
 * Default pins the deliberate background_dark_8 -> background_dark_4 shift, and dark Black pins
 * the per-style override that keeps the legacy background_dark_black_8.
 *
 * The note fixture is fully deterministic: fixed content and fixed GMT timestamps (the default
 * time zone is pinned because DateTimeUtils formats in it), and the fake SyncTimes make the note
 * read as never synced, so the Synced row is gone.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class InfoSheetScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun pinTimeZone() {
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun lightDefault() = capture(AppStyle.DEFAULT, darkMode = false, goldenName = "info_sheet_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkDefault() = capture(AppStyle.DEFAULT, darkMode = true, goldenName = "info_sheet_dark_default")

    @Test
    fun lightBlack() = capture(AppStyle.BLACK, darkMode = false, goldenName = "info_sheet_light_black")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkBlack() = capture(AppStyle.BLACK, darkMode = true, goldenName = "info_sheet_dark_black")

    private fun capture(style: AppStyle, darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(TagsActivity::class.java, style, darkMode).use { scenario ->
            scenario.onActivity { activity ->
                val host = Fragment()
                activity.supportFragmentManager.beginTransaction().add(host, "screenshot-host").commitNow()
                InfoBottomSheetDialog(host).show(activity.supportFragmentManager, sampleNote())
            }
            ScreenshotHarness.settle()
            captureScreenRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
        }
    }

    private fun sampleNote(): Note = Note("screenshot-info-note").apply {
        content = "Lemon cake recipe\n\nZest two lemons, cream the butter and sugar, fold in the flour."
        creationDate = calendarAt(2024, Calendar.JANUARY, 15, 9, 30)
        modificationDate = calendarAt(2024, Calendar.FEBRUARY, 2, 18, 45)
    }

    private fun calendarAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }
}
