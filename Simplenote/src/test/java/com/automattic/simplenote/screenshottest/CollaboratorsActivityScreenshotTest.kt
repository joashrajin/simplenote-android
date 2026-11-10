package com.automattic.simplenote.screenshottest

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.CollaboratorsActivity
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
 * Goldens for the Compose CollaboratorsScreen (the first converted screen): list and empty states,
 * each across the {light, dark} x {Default, Black} matrix, hosted by the real
 * CollaboratorsActivity so SimplenoteTheme resolves from the persisted style like production.
 * [ScreenshotCollaboratorsRepository] serves the deterministic fixture per launched note id.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class CollaboratorsActivityScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun listLightDefault() =
        capture(listNoteId, AppStyle.DEFAULT, darkMode = false, goldenName = "collaborators_activity_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun listDarkDefault() =
        capture(listNoteId, AppStyle.DEFAULT, darkMode = true, goldenName = "collaborators_activity_dark_default")

    @Test
    fun listLightBlack() =
        capture(listNoteId, AppStyle.BLACK, darkMode = false, goldenName = "collaborators_activity_light_black")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun listDarkBlack() =
        capture(listNoteId, AppStyle.BLACK, darkMode = true, goldenName = "collaborators_activity_dark_black")

    @Test
    fun emptyLightDefault() =
        capture(emptyNoteId, AppStyle.DEFAULT, darkMode = false, goldenName = "collaborators_empty_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun emptyDarkDefault() =
        capture(emptyNoteId, AppStyle.DEFAULT, darkMode = true, goldenName = "collaborators_empty_dark_default")

    @Test
    fun emptyLightBlack() =
        capture(emptyNoteId, AppStyle.BLACK, darkMode = false, goldenName = "collaborators_empty_light_black")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun emptyDarkBlack() =
        capture(emptyNoteId, AppStyle.BLACK, darkMode = true, goldenName = "collaborators_empty_dark_black")

    private val listNoteId = ScreenshotCollaboratorsRepository.NOTE_WITH_COLLABORATORS
    private val emptyNoteId = ScreenshotCollaboratorsRepository.NOTE_EMPTY

    private fun capture(noteId: String, style: AppStyle, darkMode: Boolean, goldenName: String) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CollaboratorsActivity::class.java)
            .putExtra(CollaboratorsActivity.NOTE_ID_ARG, noteId)
        ScreenshotHarness.launchSettled<CollaboratorsActivity>(intent, style, darkMode).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }
}
