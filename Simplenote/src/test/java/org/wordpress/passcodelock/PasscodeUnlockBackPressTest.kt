package org.wordpress.passcodelock

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Pair
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWindowOnBackInvokedDispatcher
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PasscodeUnlockBackPressTest {
    private lateinit var appLock: AbstractAppLock

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ReflectionHelpers.setStaticField(
                Class.forName("android.window.WindowOnBackInvokedDispatcher"),
                "ENABLE_PREDICTIVE_BACK",
                true,
            )
            ReflectionHelpers.callInstanceMethod<Any?>(
                RuntimeEnvironment.getApplication().applicationInfo,
                "setEnableOnBackInvokedCallback",
                ClassParameter.from(Boolean::class.javaPrimitiveType, true),
            )
        }
        appLock = mock()
        AppLockManager.getInstance().setCurrentAppLock(appLock)
    }

    @After
    fun tearDown() {
        AppLockManager.getInstance().setCurrentAppLock(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ShadowWindowOnBackInvokedDispatcher.reset()
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun android15PlatformBackForcesLockAndLeavesForHome() {
        assertPlatformBackLeavesForHome()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun android13PlatformBackForcesLockAndLeavesForHome() {
        assertPlatformBackLeavesForHome()
    }

    private fun assertPlatformBackLeavesForHome() {
        val activity = Robolectric.buildActivity(PasscodeUnlockActivity::class.java).create().get()
        val callback = defaultPriorityCallbacks(activity).single()

        shadowOf(activity).clearNextStartedActivities()
        callback.onBackInvoked()

        assertBackLeavesForHome(activity)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    @Suppress("DEPRECATION")
    fun legacyBackStillForcesLockAndLeavesForHome() {
        val activity = Robolectric.buildActivity(PasscodeUnlockActivity::class.java).create().get()

        shadowOf(activity).clearNextStartedActivities()
        activity.onBackPressed()

        assertBackLeavesForHome(activity)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun destroyingTheActivityUnregistersItsPlatformCallback() {
        val controller = Robolectric.buildActivity(PasscodeUnlockActivity::class.java).create()
        val activity = controller.get()

        assertEquals(1, defaultPriorityCallbacks(activity).size)

        controller.destroy()

        assertTrue(defaultPriorityCallbacks(activity).isEmpty())
    }

    private fun assertBackLeavesForHome(activity: PasscodeUnlockActivity) {
        verify(appLock).forcePasswordLock()
        val shadowActivity = shadowOf(activity)
        val startedIntent = shadowActivity.nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, startedIntent.action)
        assertTrue(startedIntent.categories.contains(Intent.CATEGORY_HOME))
        assertNull(startedIntent.component)
        assertNull(shadowActivity.nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    private fun defaultPriorityCallbacks(activity: PasscodeUnlockActivity): List<OnBackInvokedCallback> {
        val callbacks = ReflectionHelpers.getField<List<Pair<OnBackInvokedCallback, Int>>>(
            activity.onBackInvokedDispatcher,
            "mCallbacks",
        )
        return callbacks.filter {
            it.second == OnBackInvokedDispatcher.PRIORITY_DEFAULT
        }.map { it.first }
    }
}
