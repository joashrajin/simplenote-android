package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyViewTouchTargetTest {
    private lateinit var action: View
    private var minimumTarget = 0

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Simplestyle)
        val root = LayoutInflater.from(context).inflate(R.layout.empty_view, null) as ViewGroup
        val metrics = context.resources.displayMetrics

        action = root.findViewById<TextView>(R.id.button).apply {
            text = context.getString(R.string.empty_notes_search_button, "test")
            visibility = View.VISIBLE
        }
        minimumTarget = context.resources.getDimensionPixelSize(R.dimen.minimum_target)

        root.measure(exactly(metrics.widthPixels), exactly(metrics.heightPixels))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    @Test
    fun searchActionDeclaresMinimumTouchTargetHeight() {
        assertEquals(minimumTarget, action.minimumHeight)
    }

    @Test
    fun searchActionMeasuresAtLeastMinimumTouchTargetHeight() {
        assertEquals(View.VISIBLE, action.visibility)
        assertTrue(action.isClickable)
        assertTrue(action.isFocusable)
        assertTrue(
            "Expected a height of at least $minimumTarget px but measured ${action.measuredHeight} px",
            action.measuredHeight >= minimumTarget
        )
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }
}
