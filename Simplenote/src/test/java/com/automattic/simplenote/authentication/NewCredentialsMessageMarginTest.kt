package com.automattic.simplenote.authentication

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NewCredentialsMessageMarginTest {
    @Test
    fun passwordMessageKeepsItsHalfDefaultBottomMargin() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Authentication)
        val layout = LayoutInflater.from(context).inflate(R.layout.new_activity_credentials, null)
        val message = layout.findViewById<View>(R.id.login_with_password_email_message)
        val margins = message.layoutParams as ViewGroup.MarginLayoutParams

        assertEquals(context.resources.getDimensionPixelSize(R.dimen.margin_default_half), margins.bottomMargin)
    }
}
