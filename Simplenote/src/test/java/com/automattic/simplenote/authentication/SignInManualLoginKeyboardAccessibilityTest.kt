package com.automattic.simplenote.authentication

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.automattic.simplenote.R
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
class SignInManualLoginKeyboardAccessibilityTest {
    private lateinit var root: ViewGroup
    private lateinit var manualLogin: TextView

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Authentication)
        root = LayoutInflater.from(context).inflate(R.layout.fragment_login, null) as ViewGroup
        manualLogin = root.findViewById(R.id.sign_in_login_manually)
        manualLogin.setOnClickListener { }
    }

    @Test
    fun boundManualLoginOptionIsFocusableOutsideTouchMode() {
        assertTrue(manualLogin.isClickable)
        assertTrue(manualLogin.isFocusable)
        assertFalse(manualLogin.isFocusableInTouchMode)
    }

    @Test
    fun boundManualLoginOptionAppearsInKeyboardTraversal() {
        val focusableViews = arrayListOf<View>()

        root.addFocusables(focusableViews, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)

        assertTrue(focusableViews.contains(manualLogin))
    }
}
