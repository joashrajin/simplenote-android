package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DrawerActionEditAccessibilityTest {
    private lateinit var editAction: TextView
    private var minimumTarget = 0

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Simplestyle)
        val root = LayoutInflater.from(context).inflate(R.layout.drawer_action_edit, null)
        editAction = root.findViewById(R.id.edit)
        minimumTarget = context.resources.getDimensionPixelSize(R.dimen.minimum_target)
    }

    @Test
    fun editActionUsesMinimumTouchHeight() {
        assertTrue(editAction.isClickable)
        assertTrue(editAction.isFocusable)
        assertEquals(minimumTarget, editAction.minHeight)

        editAction.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertTrue(editAction.measuredHeight >= minimumTarget)
    }

    @Test
    fun editActionUsesMinimumTouchWidth() {
        assertEquals(minimumTarget, editAction.minWidth)

        editAction.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertTrue(editAction.measuredWidth >= minimumTarget)
    }
}
