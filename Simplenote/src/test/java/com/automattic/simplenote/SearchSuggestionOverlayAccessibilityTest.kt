package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
class SearchSuggestionOverlayAccessibilityTest {
    @Test
    fun overlayIsExcludedWhileSuggestionDescendantsRemainAvailable() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Simplestyle)
        controller.setup()

        try {
            val root = LayoutInflater.from(activity).inflate(R.layout.fragment_notes_list, null) as ViewGroup
            val overlay = root.findViewById<ViewGroup>(R.id.suggestion_layout)
            val suggestion = TextView(activity).apply {
                contentDescription = "Suggestion"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }

            activity.setContentView(root)
            overlay.visibility = View.VISIBLE
            overlay.addView(suggestion)

            val accessibleChildren = arrayListOf<View>()
            root.addChildrenForAccessibility(accessibleChildren)

            assertTrue(overlay.isClickable)
            assertTrue(overlay.isFocusable)
            assertFalse(overlay.isImportantForAccessibility)
            assertTrue(overlay.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            assertTrue(suggestion.isImportantForAccessibility)
            assertFalse(accessibleChildren.contains(overlay))
            assertTrue(accessibleChildren.contains(suggestion))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
