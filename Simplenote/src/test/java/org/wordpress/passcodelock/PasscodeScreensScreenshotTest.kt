package org.wordpress.passcodelock

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.screenshottest.ScreenshotHarness
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the passcode keypad screens, launched with the theme the Simplenote manifest gives
 * them (Theme.Simplestyle.Passcode). The unlock capture has two digits entered so the pin field's
 * text rendering is pinned too; the manage capture is the change-passcode entry with its custom
 * prompt. Light only: the theme is not style-aware and neither it nor the passcodelock_* colors
 * have a night variant, so a night capture would be the same image.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = ScreenshotHarness.DEVICE)
class PasscodeScreensScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        AppLockManager.getInstance().setCurrentAppLock(DefaultAppLock(application))
    }

    @Test
    fun unlockLight() = capture(
        Intent(ApplicationProvider.getApplicationContext(), PasscodeUnlockActivity::class.java),
        goldenName = "passcode_unlock_light",
        enteredDigits = listOf(R.id.button1, R.id.button2),
    )

    @Test
    fun managePasswordLight() = capture(
        Intent(ApplicationProvider.getApplicationContext(), PasscodeManagePasswordActivity::class.java)
            .putExtra(PasscodeManagePasswordActivity.KEY_TYPE, PasscodePreferenceFragmentCompat.CHANGE_PASSWORD)
            .putExtra(
                AbstractPasscodeKeyboardActivity.KEY_MESSAGE,
                ApplicationProvider.getApplicationContext<Application>()
                    .getString(R.string.passcode_enter_old_passcode),
            ),
        goldenName = "passcode_manage_password_light",
    )

    private fun capture(intent: Intent, goldenName: String, enteredDigits: List<Int> = emptyList()) {
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                enteredDigits.forEach { activity.findViewById<View>(it).performClick() }
            }
            ScreenshotHarness.settle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(ScreenshotHarness.goldenFilePath(goldenName))
            }
        }
    }
}
