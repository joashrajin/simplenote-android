package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.wordpress.passcodelock.PasscodeUnlockActivity

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SimplenoteAppLockTest {
    private lateinit var appLock: SimplenoteAppLock
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        appLock = SimplenoteAppLock(application)
        appLock.setPassword(PIN)
    }

    @After
    fun tearDown() {
        appLock.setPassword(null)
    }

    @Test
    fun lightWidgetRecreationDoesNotLaunchAnotherUnlockAfterSuccessfulAuthentication() {
        assertWidgetRecreationDoesNotLaunchAnotherUnlock<NoteWidgetLightConfigureActivity>()
    }

    @Test
    fun darkWidgetRecreationDoesNotLaunchAnotherUnlockAfterSuccessfulAuthentication() {
        assertWidgetRecreationDoesNotLaunchAnotherUnlock<NoteWidgetDarkConfigureActivity>()
    }

    private inline fun <reified T : Activity> assertWidgetRecreationDoesNotLaunchAnotherUnlock() {
        val original = widgetActivity<T>(isChangingConfigurations = true)
        val replacement = widgetActivity<T>()
        val later = widgetActivity<T>()

        appLock.onActivityResumed(original)
        assertUnlockStarted(original)
        appLock.onActivityPaused(original)
        appLock.onActivityDestroyed(original)

        assertTrue(appLock.verifyPassword(PIN))

        appLock.onActivityResumed(replacement)
        verify(replacement, never()).startActivity(any())
        assertNull(shadowOf(application).peekNextStartedActivity())

        appLock.onActivityDestroyed(replacement)

        appLock.onActivityResumed(later)
        assertUnlockStarted(later)
    }

    private inline fun <reified T : Activity> widgetActivity(isChangingConfigurations: Boolean = false): T {
        return mock {
            on { applicationContext } doReturn application
            on { getApplication() } doReturn application
            on { this.isChangingConfigurations } doReturn isChangingConfigurations
        }
    }

    private fun assertUnlockStarted(activity: Activity) {
        val intent = argumentCaptor<Intent>().run {
            verify(activity).startActivity(capture())
            firstValue
        }

        assertEquals(PasscodeUnlockActivity::class.java.name, intent.component?.className)
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private companion object {
        const val PIN = "1234"
    }
}
