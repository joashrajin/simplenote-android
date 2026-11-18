package com.automattic.simplenote.utils

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the legacy HTML parsing on both sides of the API 24 branch: FROM_HTML_MODE_LEGACY on N+ and
 * the single-argument overload on API 23.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HtmlCompatTest {
    @Test
    fun parsesTheMarkupCallersUseOnN() = assertParsesCallerMarkup()

    @Test
    @Config(sdk = [23])
    fun parsesTheMarkupCallersUseBeforeN() = assertParsesCallerMarkup()

    @Test
    fun keepsLegacyBlockSpacingOnN() = assertLegacyBlockSpacing()

    @Test
    @Config(sdk = [23])
    fun keepsLegacyBlockSpacingBeforeN() = assertLegacyBlockSpacing()

    private fun assertParsesCallerMarkup() {
        val spanned = HtmlCompat.fromHtml(
            "<b>bold</b> <a href=\"https://simplenote.com\">link</a> <font color=\"#ff0000\">red</font>"
        )

        assertEquals("bold link red", spanned.toString())

        val bold = spanned.getSpans(0, spanned.length, StyleSpan::class.java).single()
        assertEquals(Typeface.BOLD, bold.style)
        assertRange(spanned, bold, 0, 4)

        val link = spanned.getSpans(0, spanned.length, URLSpan::class.java).single()
        assertEquals("https://simplenote.com", link.url)
        assertRange(spanned, link, 5, 9)

        val color = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java).single()
        assertEquals(Color.RED, color.foregroundColor)
        assertRange(spanned, color, 10, 13)
    }

    private fun assertLegacyBlockSpacing() {
        assertEquals("first\n\nsecond\n\n", HtmlCompat.fromHtml("<p>first</p><p>second</p>").toString())
        assertEquals("first\nsecond", HtmlCompat.fromHtml("first<br>second").toString())
        assertEquals("", HtmlCompat.fromHtml("").toString())
    }

    private fun assertRange(spanned: Spanned, span: Any, start: Int, end: Int) {
        assertEquals(start, spanned.getSpanStart(span))
        assertEquals(end, spanned.getSpanEnd(span))
    }
}
