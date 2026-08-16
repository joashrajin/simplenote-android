package com.automattic.simplenote

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
@Config(application = Application::class, sdk = [23, 35])
class CollaboratorRowKeyboardAccessibilityTest {
    private lateinit var row: ViewGroup
    private lateinit var removeAction: ImageButton

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Simplestyle)
        row = LayoutInflater.from(context).inflate(R.layout.collaborator_row, null) as ViewGroup
        removeAction = row.findViewById(R.id.collaborator_remove_button)
        removeAction.setOnClickListener { }
        removeAction.setOnLongClickListener { true }
    }

    @Test
    fun boundRemoveActionIsFocusableOutsideTouchMode() {
        assertTrue(removeAction.isFocusable)
        assertFalse(removeAction.isFocusableInTouchMode)
    }

    @Test
    fun rowExposesRemoveActionToKeyboardTraversal() {
        val focusableViews = arrayListOf<View>()

        row.addFocusables(focusableViews, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)

        assertTrue(focusableViews.contains(removeAction))
    }
}
