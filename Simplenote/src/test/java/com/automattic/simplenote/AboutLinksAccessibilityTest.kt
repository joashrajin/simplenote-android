package com.automattic.simplenote

import android.app.Application
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AboutLinksAccessibilityTest {
    @Test
    fun legalLinksUseMinimumTouchTargets() {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Theme_Simplestyle_About,
        )
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_about, null)
        val minimumTarget = context.resources.getDimensionPixelSize(R.dimen.minimum_target)

        listOf(
            R.id.about_privacy,
            R.id.about_terms,
            R.id.about_california,
        ).forEach { id ->
            val link = root.findViewById<TextView>(id)

            assertTrue(link.isClickable)
            assertTrue(link.isFocusable)
            assertEquals(minimumTarget, link.minHeight)
            assertEquals(minimumTarget, link.minWidth)
            assertEquals(Gravity.CENTER_VERTICAL, link.gravity and Gravity.VERTICAL_GRAVITY_MASK)

            link.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            assertTrue(link.measuredHeight >= minimumTarget)
            assertTrue(link.measuredWidth >= minimumTarget)
        }
    }
}
