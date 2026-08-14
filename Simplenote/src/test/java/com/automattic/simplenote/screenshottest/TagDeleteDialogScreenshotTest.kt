package com.automattic.simplenote.screenshottest

import androidx.lifecycle.ViewModelProvider
import com.automattic.simplenote.TagsActivity
import com.automattic.simplenote.viewmodels.TagsViewModel
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
 * Baseline goldens for the tag-delete confirmation dialog, driven through the real
 * TagsActivity -> TagsViewModel event path and captured with the dialog window on top of the
 * activity. Proof matrix for the harness: {light, dark} x {Default, Black}.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class TagDeleteDialogScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun lightDefault() = capture(AppStyle.DEFAULT, darkMode = false, goldenName = "tag_delete_dialog_light_default")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkDefault() = capture(AppStyle.DEFAULT, darkMode = true, goldenName = "tag_delete_dialog_dark_default")

    @Test
    fun lightBlack() = capture(AppStyle.BLACK, darkMode = false, goldenName = "tag_delete_dialog_light_black")

    @Test
    @Config(qualifiers = ScreenshotHarness.NIGHT)
    fun darkBlack() = capture(AppStyle.BLACK, darkMode = true, goldenName = "tag_delete_dialog_dark_black")

    private fun capture(style: AppStyle, darkMode: Boolean, goldenName: String) {
        ScreenshotHarness.launchSettled(TagsActivity::class.java, style, darkMode).use { scenario ->
            scenario.onActivity { activity ->
                // Same instance as the activity's `by viewModels()`; posting the delete event
                // makes the activity show its real confirmation dialog.
                ViewModelProvider(activity)[TagsViewModel::class.java]
                    .clickDeleteTag(ScreenshotHarness.SAMPLE_TAG_ITEMS.first())
            }
            ScreenshotHarness.settle()
            captureScreenRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
        }
    }
}
