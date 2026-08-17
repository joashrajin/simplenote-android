package com.automattic.simplenote

import android.app.Application
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class AboutCloseActionAccessibilityTest {
    @Test
    fun closeActionIsNamedForItsBehavior() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        val closeAction = navigationButton(toolbar)

        try {
            val node = closeAction.createAccessibilityNodeInfo()
            assertEquals(activity.getString(R.string.description_close), node.contentDescription.toString())
            assertEquals(node.contentDescription, toolbar.navigationContentDescription)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun closeAccessibilityActionFinishesAbout() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()
        val closeAction = navigationButton(activity.findViewById(R.id.toolbar))

        try {
            assertFalse(activity.isFinishing)
            assertTrue(closeAction.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertTrue(activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun navigationButton(toolbar: Toolbar): ImageButton =
        (0 until toolbar.childCount)
            .map(toolbar::getChildAt)
            .filterIsInstance<ImageButton>()
            .single { it.isClickable && it.drawable != null }
}
