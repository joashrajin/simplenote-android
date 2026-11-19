package org.wordpress.passcodelock

import android.app.Application
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The passcode screens extend the platform Activity, which AppCompat's pre-13 app language does not reach. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [32])
class PasscodeAppLanguageTest {
    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun appLanguageReachesThePasscodeScreens() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))

        val activity = Robolectric.buildActivity(PasscodeUnlockActivity::class.java).get()

        assertEquals("fr", activity.resources.configuration.locales.toLanguageTags())
        assertEquals("Entrer ancien code d'accès", activity.getString(R.string.passcode_enter_old_passcode))
    }

    @Test
    fun withoutAnAppLanguageThePasscodeScreensFollowTheSystem() {
        val activity = Robolectric.buildActivity(PasscodeUnlockActivity::class.java).get()

        assertEquals("Enter your old passcode", activity.getString(R.string.passcode_enter_old_passcode))
    }
}
