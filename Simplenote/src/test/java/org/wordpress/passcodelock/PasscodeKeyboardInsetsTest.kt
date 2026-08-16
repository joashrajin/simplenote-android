package org.wordpress.passcodelock

import android.content.Context
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PasscodeKeyboardInsetsTest {
    @Test
    fun keyboardRootUsesTheLargestSystemBarOrCutoutInsetOnEveryEdge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = View(context)
        root.setPadding(1, 2, 3, 4)
        val windowInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 8, 12, 16))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(20, 6, 10, 24))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 40))
            .build()

        AbstractPasscodeKeyboardActivity.applyKeyboardInsets(root)
        val returnedInsets = ViewCompat.dispatchApplyWindowInsets(root, windowInsets)

        assertEquals(21, root.paddingLeft)
        assertEquals(10, root.paddingTop)
        assertEquals(15, root.paddingRight)
        assertEquals(28, root.paddingBottom)
        assertEquals(Insets.NONE, returnedInsets.getInsets(WindowInsetsCompat.Type.systemBars()))
        assertEquals(Insets.NONE, returnedInsets.getInsets(WindowInsetsCompat.Type.displayCutout()))
        assertEquals(16, returnedInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom)

        val smallerInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(3, 6, 9, 12))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, smallerInsets)
        assertEquals(4, root.paddingLeft)
        assertEquals(8, root.paddingTop)
        assertEquals(12, root.paddingRight)
        assertEquals(16, root.paddingBottom)
    }

    @Test
    fun phoneActivityUsesTheInsetRootAndBackground() {
        val controller = Robolectric.buildActivity(PasscodeUnlockActivity::class.java).create()
        val root = controller.get().findViewById<View>(R.id.passcodelock_root)

        assertEquals(R.id.passcodelock_root, root.id)
        assertNotNull(root.background)
        controller.destroy()
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun bothPasscodeActivitiesInstallTheInsetListener() {
        assertActivityAppliesInsets(PasscodeManagePasswordActivity::class.java, listOf(0, 0, 0, 0))
        assertActivityAppliesInsets(PasscodeUnlockActivity::class.java, listOf(0, 0, 0, 0))
    }

    @Test
    @Config(sdk = [34], qualifiers = "sw600dp")
    fun preAndroid15ActivitiesPreserveLegacyPaddingAndInsets() {
        assertActivityPreservesLegacyInsets(PasscodeManagePasswordActivity::class.java)
        assertActivityPreservesLegacyInsets(PasscodeUnlockActivity::class.java)
    }

    private fun <T : AbstractPasscodeKeyboardActivity> assertActivityAppliesInsets(
        activityClass: Class<T>,
        expectedInitialPadding: List<Int>,
    ) {
        val controller = Robolectric.buildActivity(activityClass).create()
        val root = controller.get().findViewById<View>(R.id.passcodelock_root)
        val initialPadding = listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        assertEquals(expectedInitialPadding, initialPadding)
        val windowInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 8, 12, 16))
            .build()

        ViewCompat.dispatchApplyWindowInsets(root, windowInsets)

        assertEquals(initialPadding[0] + 4, root.paddingLeft)
        assertEquals(initialPadding[1] + 8, root.paddingTop)
        assertEquals(initialPadding[2] + 12, root.paddingRight)
        assertEquals(initialPadding[3] + 16, root.paddingBottom)
        controller.destroy()
    }

    private fun <T : AbstractPasscodeKeyboardActivity> assertActivityPreservesLegacyInsets(activityClass: Class<T>) {
        val controller = Robolectric.buildActivity(activityClass).create()
        val root = controller.get().findViewById<View>(R.id.passcodelock_root)
        val initialPadding = listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        val windowInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 8, 12, 16))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 40))
            .build()

        val returnedInsets = ViewCompat.dispatchApplyWindowInsets(root, windowInsets)

        assertEquals(initialPadding, listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom))
        assertEquals(40, returnedInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
        controller.destroy()
    }
}
