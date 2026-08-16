package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
class WebViewErrorTouchTargetTest {
    private lateinit var action: View
    private var minimumTarget = 0

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Simplestyle)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_note_error, null) as ViewGroup
        val metrics = context.resources.displayMetrics

        root.visibility = View.VISIBLE
        root.measure(exactly(metrics.widthPixels), exactly(metrics.heightPixels))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        action = root.findViewById(R.id.button)
        minimumTarget = context.resources.getDimensionPixelSize(R.dimen.minimum_target)
    }

    @Test
    fun recoveryActionDeclaresTheMinimumTouchTargetHeight() {
        assertEquals(minimumTarget, action.minimumHeight)
    }

    @Test
    fun recoveryActionMeasuresAtLeastTheMinimumTouchTargetHeight() {
        assertTrue(action.isClickable)
        assertTrue(action.isFocusable)
        assertTrue(action.measuredHeight >= minimumTarget)
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }
}
