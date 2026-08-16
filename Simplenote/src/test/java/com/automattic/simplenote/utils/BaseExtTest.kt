package com.automattic.simplenote.utils

import android.app.Application
import android.os.Bundle
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "notnight")
class BaseExtTest {
    @Test
    fun htmlColorsAreSixLowercaseRgbDigitsWithoutPrefix() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()

        assertEquals("3361cc", activity.getColorStr(R.color.text_link))
        assertEquals("ffabaf", activity.getColorStr(R.color.red_10))
        assertEquals("ffffff", activity.getColorStr(R.color.style_locked_icon))
        assertEquals("000000", activity.getColorStr(R.color.style_locked_background))
    }

    @Test
    fun callerPrefixCreatesTheExpectedForegroundSpan() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val email = "user@example.com"
        val colorLink = activity.getColorStr(R.color.text_link)
        val text = HtmlCompat.fromHtml("<b><font color=\"#$colorLink\">$email<font/></b>")
        val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)

        assertEquals(1, spans.size)
        assertEquals(ContextCompat.getColor(activity, R.color.text_link), spans.single().foregroundColor)
        assertEquals(0, text.getSpanStart(spans.single()))
        assertEquals(email.length, text.getSpanEnd(spans.single()))
    }

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_Simplestyle)
            super.onCreate(savedInstanceState)
        }
    }
}
