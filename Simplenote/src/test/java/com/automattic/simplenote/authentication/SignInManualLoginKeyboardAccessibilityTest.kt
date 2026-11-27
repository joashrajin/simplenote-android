package com.automattic.simplenote.authentication

import android.app.Application
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.automattic.simplenote.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// API 26+ makes clickable views focusable by default; API 23-25 needs the explicit attribute.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class SignInManualLoginKeyboardAccessibilityTest {
    @Test
    fun manualLoginOptionIsKeyboardFocusableOnceClickable() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Authentication)
        val layout = LayoutInflater.from(context).inflate(R.layout.fragment_login, null)
        val manualLogin = layout.findViewById<TextView>(R.id.sign_in_login_manually)

        manualLogin.setOnClickListener { }

        assertTrue(manualLogin.isFocusable)
    }
}
