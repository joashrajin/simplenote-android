package org.wordpress.passcodelock

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Drives the fragment the way PreferencesActivity hosts it: not inflating its own XML, with the
 * host's passcode preferences handed over through setPreferences. Nothing else refreshes the
 * preferences here, so a title change after a result can only come from the result callback.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class PasscodePreferenceFragmentCompatTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var appLock: DefaultAppLock
    private lateinit var activity: AppCompatActivity
    private lateinit var togglePreference: Preference
    private lateinit var changePreference: Preference

    @Before
    fun setUp() {
        appLock = DefaultAppLock(application)
        AppLockManager.getInstance().setCurrentAppLock(appLock)
    }

    @Test
    fun toggleWhileUnlockedLaunchesEnableAndOkResultRefreshesPrefs() {
        launchFragment()
        assertEquals(application.getString(R.string.passcode_turn_on), togglePreference.title)
        assertFalse(changePreference.isEnabled)

        val started = click(togglePreference)
        assertManagePasswordIntent(started, PasscodePreferenceFragmentCompat.ENABLE_PASSLOCK)

        appLock.setPassword(PASSCODE)
        deliverResult(started, Activity.RESULT_OK)

        assertEquals(application.getString(R.string.passcode_turn_off), togglePreference.title)
        assertTrue(changePreference.isEnabled)
    }

    @Test
    fun toggleWhileLockedLaunchesDisableAndOkResultRefreshesPrefs() {
        appLock.setPassword(PASSCODE)
        launchFragment()
        assertEquals(application.getString(R.string.passcode_turn_off), togglePreference.title)

        val started = click(togglePreference)
        assertManagePasswordIntent(started, PasscodePreferenceFragmentCompat.DISABLE_PASSLOCK)

        appLock.setPassword(null)
        deliverResult(started, Activity.RESULT_OK)

        assertEquals(application.getString(R.string.passcode_turn_on), togglePreference.title)
        assertFalse(changePreference.isEnabled)
    }

    @Test
    fun changePasscodeLaunchesChangeWithOldPasscodePrompt() {
        appLock.setPassword(PASSCODE)
        launchFragment()

        val started = click(changePreference)

        assertManagePasswordIntent(started, PasscodePreferenceFragmentCompat.CHANGE_PASSWORD)
        assertEquals(
            application.getString(R.string.passcode_enter_old_passcode),
            started.getStringExtra(AbstractPasscodeKeyboardActivity.KEY_MESSAGE),
        )
    }

    @Test
    fun cancelledResultLeavesPrefsUntouched() {
        launchFragment()
        val started = click(togglePreference)

        appLock.setPassword(PASSCODE)
        deliverResult(started, Activity.RESULT_CANCELED)

        assertEquals(application.getString(R.string.passcode_turn_on), togglePreference.title)
        assertFalse(changePreference.isEnabled)
    }

    private fun launchFragment() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_AppCompat_Light)
        activity = controller.setup().get()
        val fragment = PasscodePreferenceFragmentCompat().apply {
            arguments = bundleOf(PasscodePreferenceFragmentCompat.KEY_SHOULD_INFLATE to false)
        }
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
        togglePreference = preference(R.string.pref_key_passcode_toggle)
        changePreference = preference(R.string.pref_key_change_passcode)
        fragment.setPreferences(togglePreference, changePreference)
    }

    private fun preference(keyRes: Int): Preference =
        Preference(activity).apply { key = application.getString(keyRes) }

    private fun click(preference: Preference): Intent {
        assertTrue(preference.onPreferenceClickListener!!.onPreferenceClick(preference))
        return shadowOf(activity).nextStartedActivityForResult.intent
    }

    private fun assertManagePasswordIntent(intent: Intent, expectedType: Int) {
        assertEquals(PasscodeManagePasswordActivity::class.java.name, intent.component?.className)
        assertEquals(expectedType, intent.getIntExtra(PasscodeManagePasswordActivity.KEY_TYPE, -1))
    }

    private fun deliverResult(started: Intent, resultCode: Int) {
        shadowOf(activity).receiveResult(started, resultCode, null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        const val PASSCODE = "1234"
    }
}
