package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35], qualifiers = "large-land")
class LargeLandscapeEditorPlaceholderAccessibilityTest {
    @Test
    fun placeholderRetainsClickAndFocusBehavior() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Style_Default)
        controller.setup()

        try {
            val root = LayoutInflater.from(activity).inflate(R.layout.fragment_note_editor, null) as ViewGroup
            val placeholder = root.findViewById<ViewGroup>(R.id.placeholder)

            activity.setContentView(root)
            placeholder.visibility = View.VISIBLE

            val focusableViews = arrayListOf<View>()
            (placeholder.parent as ViewGroup).addFocusables(
                focusableViews,
                View.FOCUS_FORWARD,
                View.FOCUSABLES_ALL
            )

            assertTrue(placeholder.isClickable)
            assertTrue(placeholder.isFocusable)
            assertFalse(placeholder.hasOnClickListeners())
            assertFalse(placeholder.performClick())
            assertTrue(focusableViews.contains(placeholder))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun placeholderIsExcludedWhileLogoRemainsAvailable() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Style_Default)
        controller.setup()

        try {
            val root = LayoutInflater.from(activity).inflate(R.layout.fragment_note_editor, null) as ViewGroup
            val placeholder = root.findViewById<ViewGroup>(R.id.placeholder)
            val logo = placeholder.getChildAt(0)

            activity.setContentView(root)
            placeholder.visibility = View.VISIBLE

            val accessibleChildren = arrayListOf<View>()
            (placeholder.parent as ViewGroup).addChildrenForAccessibility(accessibleChildren)

            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, placeholder.importantForAccessibility)
            assertFalse(placeholder.isImportantForAccessibility)
            assertFalse(accessibleChildren.contains(placeholder))
            assertEquals(activity.getString(R.string.logo), logo.contentDescription)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, logo.importantForAccessibility)
            assertTrue(logo.isImportantForAccessibility)
            assertTrue(accessibleChildren.contains(logo))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
