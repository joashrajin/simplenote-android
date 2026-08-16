package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class FullScreenDialogRootAccessibilityTest {
    @Test
    fun dialogContainerIsExcludedWhileContentRemainsAvailable() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Simplestyle)
        controller.setup()

        try {
            val host = FrameLayout(activity)
            val dialog = LayoutInflater.from(activity).inflate(R.layout.fragment_full_screen_dialog, host, false)
                as ViewGroup
            val content = dialog.findViewById<ViewGroup>(R.id.full_screen_dialog_fragment_content)
            val action = TextView(activity).apply {
                contentDescription = "Action"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }

            activity.setContentView(host)
            host.addView(dialog)
            dialog.isFocusableInTouchMode = true
            assertTrue(dialog.requestFocus())
            content.addView(action)

            val accessibleChildren = arrayListOf<View>()
            host.addChildrenForAccessibility(accessibleChildren)

            assertTrue(dialog.isClickable)
            assertTrue(dialog.isFocusable)
            assertTrue(dialog.isFocusableInTouchMode)
            assertTrue(dialog.hasFocus())
            assertFalse(dialog.isImportantForAccessibility)
            assertTrue(dialog.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            assertTrue(action.isImportantForAccessibility)
            assertFalse(accessibleChildren.contains(dialog))
            assertTrue(accessibleChildren.contains(action))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
