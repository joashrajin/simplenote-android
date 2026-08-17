package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class ToolbarProgressAccessibilityTest {
    @Suppress("DEPRECATION")
    @Test
    fun noteLoadingIndicatorHasContextualAccessibilityName() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Style_Default)
        controller.setup()

        try {
            val host = FrameLayout(activity)
            val progressBar = LayoutInflater.from(activity)
                .inflate(R.layout.progressbar_toolbar, host, false) as ProgressBar
            host.addView(progressBar)
            activity.setContentView(host)
            shadowOf(Looper.getMainLooper()).idle()

            val expectedName = activity.getString(R.string.loading_notes)
            val node = progressBar.createAccessibilityNodeInfo()
            val accessibleChildren = arrayListOf<View>()
            host.addChildrenForAccessibility(accessibleChildren)

            try {
                assertTrue(progressBar.isIndeterminate)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, progressBar.importantForAccessibility)
                assertTrue(accessibleChildren.contains(progressBar))
                assertEquals(ProgressBar::class.java.name, node.className)
                assertEquals(expectedName, progressBar.contentDescription)
                assertEquals(expectedName, node.contentDescription)
            } finally {
                node.recycle()
            }
        } finally {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
