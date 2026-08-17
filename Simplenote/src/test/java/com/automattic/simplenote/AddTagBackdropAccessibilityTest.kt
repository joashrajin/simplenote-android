package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class AddTagBackdropAccessibilityTest {
    @Test
    fun backdropIsExcludedWhileDialogControlsRemainAvailable() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Transparent_Dialog)
        controller.setup()

        try {
            val host = FrameLayout(activity)
            val backdrop = LayoutInflater.from(activity).inflate(R.layout.activity_tag_add, host, false)
                as ViewGroup
            val dialog = backdrop.findViewById<ViewGroup>(R.id.layout)
            val input = backdrop.findViewById<View>(R.id.tag_input)
            val cancel = backdrop.findViewById<View>(R.id.button_negative)
            val save = backdrop.findViewById<View>(R.id.button_positive)

            activity.setContentView(host)
            host.addView(backdrop)
            backdrop.setOnClickListener { }
            dialog.setOnClickListener(null)
            save.isEnabled = true

            val accessibleChildren = arrayListOf<View>()
            host.addChildrenForAccessibility(accessibleChildren)
            val focusableViews = arrayListOf<View>()
            host.addFocusables(focusableViews, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)

            assertTrue(backdrop.isClickable)
            assertTrue(dialog.isClickable)
            assertFalse(backdrop.isFocusable)
            assertFalse(dialog.isFocusable)
            assertFalse(backdrop.isImportantForAccessibility)
            assertFalse(dialog.isImportantForAccessibility)
            assertTrue(backdrop.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            assertTrue(dialog.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            assertFalse(accessibleChildren.contains(backdrop))
            assertFalse(accessibleChildren.contains(dialog))
            assertTrue(accessibleChildren.contains(input))
            assertTrue(accessibleChildren.contains(cancel))
            assertTrue(accessibleChildren.contains(save))
            assertFalse(focusableViews.contains(backdrop))
            assertFalse(focusableViews.contains(dialog))
            assertTrue(focusableViews.contains(input))
            assertTrue(focusableViews.contains(cancel))
            assertTrue(focusableViews.contains(save))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun backdropClickBehaviorRemainsAvailable() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val backdrop = LayoutInflater.from(activity).inflate(R.layout.activity_tag_add, null)
        val dialog = backdrop.findViewById<View>(R.id.layout)
        var clicked = false

        backdrop.setOnClickListener { clicked = true }
        dialog.setOnClickListener(null)

        assertTrue(dialog.isClickable)
        assertFalse(dialog.performClick())
        assertFalse(clicked)
        assertTrue(backdrop.performClick())
        assertTrue(clicked)
    }
}
