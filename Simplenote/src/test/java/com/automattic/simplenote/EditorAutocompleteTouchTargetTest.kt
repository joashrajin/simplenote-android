package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
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
class EditorAutocompleteTouchTargetTest {
    private lateinit var row: TextView
    private var minimumTarget = 0

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Default)
        val metrics = context.resources.displayMetrics

        row = LayoutInflater.from(context).inflate(R.layout.autocomplete_list_item, null) as TextView
        row.text = "Tag Name"
        row.measure(exactly(metrics.widthPixels), unspecified())
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)

        minimumTarget = context.resources.getDimensionPixelSize(R.dimen.minimum_target)
    }

    @Test
    fun suggestionRowDeclaresMinimumTouchTargetHeight() {
        assertEquals(minimumTarget, row.minimumHeight)
    }

    @Test
    fun suggestionRowMatchesDropdownTargetHeight() {
        assertEquals(
            "Expected a height of $minimumTarget px but measured ${row.measuredHeight} px",
            minimumTarget,
            row.measuredHeight
        )
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }

    private fun unspecified(): Int {
        return View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    }
}
