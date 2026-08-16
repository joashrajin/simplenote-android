package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 25, 35])
class ReferenceRowKeyboardAccessibilityTest {
    private lateinit var referenceRow: ViewGroup

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Simplestyle)
        referenceRow = LayoutInflater.from(context).inflate(R.layout.reference_list_row, null) as ViewGroup
        referenceRow.setOnClickListener { }
    }

    @Test
    fun boundReferenceRowIsFocusableOutsideTouchMode() {
        assertTrue(referenceRow.isClickable)
        assertTrue(referenceRow.isFocusable)
        assertFalse(referenceRow.isFocusableInTouchMode)
    }

    @Test
    fun boundReferenceRowAppearsInKeyboardTraversal() {
        val focusableViews = arrayListOf<View>()

        referenceRow.addFocusables(focusableViews, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)

        assertTrue(focusableViews.contains(referenceRow))
    }
}
