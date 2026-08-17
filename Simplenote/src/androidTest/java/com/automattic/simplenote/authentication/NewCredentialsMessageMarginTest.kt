package com.automattic.simplenote.authentication

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class NewCredentialsMessageMarginTest {
    @Test
    fun passwordMessageKeepsItsAsymmetricMargins() {
        val layout = inflateCredentials()
        val message = layout.findViewById<View>(R.id.login_with_password_email_message)
        val margins = message.layoutParams as LinearLayout.LayoutParams
        val expectedTop = (15 * layout.resources.displayMetrics.density).roundToInt()

        assertEquals(layout.resources.getDimensionPixelSize(R.dimen.margin_default_half), margins.bottomMargin)
        assertEquals(expectedTop, margins.topMargin)
    }

    private fun inflateCredentials(): View {
        var layout: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.Style_Authentication
            )
            layout = LayoutInflater.from(context).inflate(R.layout.new_activity_credentials, null)
        }
        return requireNotNull(layout)
    }
}
