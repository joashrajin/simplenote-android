package com.automattic.simplenote

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class HistorySliderAccessibilityTest {
    @Test
    fun revisionSliderHasANameAndRetainsItsAdjustmentSemantics() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Simplestyle)
        controller.setup()

        try {
            val root = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_history, null)
            val slider = root.findViewById<SeekBar>(R.id.seek_bar)

            activity.setContentView(root)
            root.findViewById<View>(R.id.history_slider_view).visibility = View.VISIBLE
            slider.max = 4
            slider.progress = 2
            shadowOf(Looper.getMainLooper()).idle()

            val node = slider.createAccessibilityNodeInfo()

            assertEquals(activity.getString(R.string.history), node.contentDescription)
            assertEquals(SeekBar::class.java.name, node.className)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val range = node.rangeInfo
                assertNotNull(range)
                assertEquals(0f, range.min)
                assertEquals(4f, range.max)
                assertEquals(2f, range.current)
                assertEquals(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, range.type)
            } else {
                assertTrue(
                    node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
                )
                assertTrue(
                    node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                )
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
