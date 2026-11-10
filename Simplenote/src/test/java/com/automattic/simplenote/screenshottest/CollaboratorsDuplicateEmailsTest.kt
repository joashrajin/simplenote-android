package com.automattic.simplenote.screenshottest

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.CollaboratorsActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression pin (no golden): a synced note can carry exact duplicate collaborator emails —
 * another client can produce them and the repository does not dedupe. The View world's DiffUtil
 * adapter tolerated rendering the duplicates; the Compose list must too, instead of crashing on
 * duplicate LazyColumn keys. Composing the duplicate rows happens inside launchSettled, so a
 * duplicate-key IllegalArgumentException fails this test on the main looper.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class CollaboratorsDuplicateEmailsTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun duplicateCollaboratorEmailsRenderWithoutCrashing() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CollaboratorsActivity::class.java)
            .putExtra(
                CollaboratorsActivity.NOTE_ID_ARG,
                ScreenshotCollaboratorsRepository.NOTE_WITH_DUPLICATE_COLLABORATORS
            )
        ScreenshotHarness.launchSettled<CollaboratorsActivity>(intent, AppStyle.DEFAULT, darkMode = false)
            .use { scenario ->
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
    }
}
