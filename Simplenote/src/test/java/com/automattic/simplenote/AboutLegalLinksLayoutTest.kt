package com.automattic.simplenote

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h480dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutLegalLinksLayoutTest {
    @Test
    fun legalLinksShareAvailableWidthAtLargeText() {
        val views = inflateLegalLinks(Locale.GERMAN, 2f)

        assertEquals(LinearLayout.HORIZONTAL, views.row.orientation)
        assertTrue(views.privacy.measuredWidth > 0)
        assertTrue(views.terms.measuredWidth > 0)
        val minimumShare = (views.row.width - views.divider.width) / 3
        assertTrue(
            "Privacy width ${views.privacy.measuredWidth}px is below the ${minimumShare}px minimum share",
            views.privacy.measuredWidth >= minimumShare,
        )
        assertTrue(
            "Terms width ${views.terms.measuredWidth}px is below the ${minimumShare}px minimum share",
            views.terms.measuredWidth >= minimumShare,
        )
        assertEquals(2, views.privacy.layout.lineCount)
        assertEquals(2, views.terms.layout.lineCount)
        assertCompleteAndContained(views.privacy, views.row)
        assertCompleteAndContained(views.terms, views.row)
        val privacyWeight = (views.privacy.layoutParams as LinearLayout.LayoutParams).weight
        val termsWeight = (views.terms.layoutParams as LinearLayout.LayoutParams).weight
        assertTrue(privacyWeight > 0f)
        assertEquals(privacyWeight, termsWeight)
        assertEquals(views.privacy.right, views.divider.left)
        assertEquals(views.divider.right, views.terms.left)
    }

    @Test
    fun defaultTextRemainsSingleLineAndGroupedAroundDivider() {
        val views = inflateLegalLinks(Locale.ENGLISH, 1f)

        assertEquals(1, views.privacy.layout.lineCount)
        assertEquals(1, views.terms.layout.lineCount)
        assertCompleteAndContained(views.privacy, views.row)
        assertCompleteAndContained(views.terms, views.row)
        val privacyTextRight = views.privacy.left + views.privacy.compoundPaddingLeft +
            views.privacy.layout.getLineRight(0)
        val privacyTextLeft = views.privacy.left + views.privacy.compoundPaddingLeft +
            views.privacy.layout.getLineLeft(0)
        val termsTextLeft = views.terms.left + views.terms.compoundPaddingLeft +
            views.terms.layout.getLineLeft(0)
        val termsTextRight = views.terms.left + views.terms.compoundPaddingLeft +
            views.terms.layout.getLineRight(0)
        assertTrue(views.divider.left - privacyTextRight <= views.privacy.compoundPaddingRight)
        assertTrue(termsTextLeft - views.divider.right <= views.terms.compoundPaddingLeft)
        assertTrue(abs((privacyTextLeft + termsTextRight) - views.row.width) <= 1f)
        listOf(views.privacy, views.terms).forEach {
            assertTrue(it.isClickable)
            assertTrue(it.isFocusable)
            assertTrue(it.isImportantForAccessibility)
        }
    }

    private fun assertCompleteAndContained(text: TextView, parent: ViewGroup) {
        val layout = text.layout
        val contentWidth = text.measuredWidth - text.compoundPaddingLeft - text.compoundPaddingRight
        assertEquals(text.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertEquals(0, (0 until layout.lineCount).sumOf(layout::getEllipsisCount))
        assertTrue((0 until layout.lineCount).all { layout.getLineWidth(it) <= contentWidth + 1f })
        assertTrue(text.left >= 0)
        assertTrue(text.right <= parent.width)
    }

    private fun inflateLegalLinks(locale: Locale, fontScale: Float): LegalLinkViews {
        val context = createContext(locale, fontScale)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_about, null) as ViewGroup
        val privacy = root.findViewById<TextView>(R.id.about_privacy)
        val terms = root.findViewById<TextView>(R.id.about_terms)
        root.findViewById<TextView>(R.id.about_california).text =
            legalText(context, R.string.link_california)
        privacy.text = legalText(context, R.string.link_privacy)
        terms.text = legalText(context, R.string.link_terms)
        root.measure(exactly(320), exactly(480))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val row = privacy.parent as LinearLayout
        return LegalLinkViews(row, privacy, row.getChildAt(1) as TextView, terms)
    }

    private fun legalText(context: Context, stringRes: Int) = Html.fromHtml(
        String.format(
            context.getString(stringRes),
            "<u><span style=\"color:#",
            "000000",
            "\">",
            "</span></u>",
        ),
    )

    private fun createContext(locale: Locale, fontScale: Float): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            this.fontScale = fontScale
            setLocale(locale)
        }
        return ContextThemeWrapper(
            application.createConfigurationContext(configuration),
            R.style.Theme_Simplestyle_About,
        )
    }

    private fun exactly(size: Int) =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private data class LegalLinkViews(
        val row: LinearLayout,
        val privacy: TextView,
        val divider: TextView,
        val terms: TextView,
    )
}
