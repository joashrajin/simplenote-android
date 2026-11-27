package com.automattic.simplenote.authentication

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Looper
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.automattic.simplenote.R
import com.automattic.simplenote.utils.SystemBarUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "de-w480dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignupLayoutFontScaleTest {
    @Test
    fun legalFooterCanBeScrolledIntoViewAtLargeFontScale() {
        val fixture = inflateAndLayout(2f, Locale.GERMAN)
        try {
            val scrollView = fixture.fragmentRoot as? ScrollView
            val content = scrollView?.getChildAt(0) ?: fixture.fragmentRoot

            assertTrue(
                "Expected complete footer geometry; measured=${fixture.footer.height}, " +
                    "textLayout=${fixture.footer.layout.height}, lines=${fixture.footer.layout.lineCount}",
                fixture.footer.height >= fixture.footer.layout.height +
                    fixture.footer.compoundPaddingTop + fixture.footer.compoundPaddingBottom
            )
            assertEquals(
                fixture.footer.text.length,
                fixture.footer.layout.getLineEnd(fixture.footer.layout.lineCount - 1)
            )
            assertEquals(0, (0 until fixture.footer.layout.lineCount).sumOf(fixture.footer.layout::getEllipsisCount))
            assertTrue(fixture.button.height >= fixture.button.minimumHeight)
            assertNotNull("Expected the sign-up form to be scrollable", scrollView)
            scrollView!!

            assertTrue(scrollView.isFillViewport)
            assertTrue(content.height > scrollView.height)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, content.layoutParams.width)
            assertEquals(scrollView.width, content.width)

            scrollView.scrollTo(0, content.height - scrollView.height)

            assertTrue(scrollView.scrollY > 0)
            assertTrue(fixture.footer.top >= scrollView.scrollY)
            assertTrue(fixture.footer.bottom <= scrollView.scrollY + scrollView.height)
        } finally {
            fixture.close()
        }
    }

    @Test
    @Config(qualifiers = "en-w480dp-h360dp-land-mdpi")
    fun fittingContentRetainsItsLayoutWithoutScrolling() {
        val fixture = inflateAndLayout(1f, Locale.ENGLISH)
        try {
            val scrollView = fixture.fragmentRoot as? ScrollView

            assertNotNull("Expected the sign-up form to use a fill-viewport ScrollView", scrollView)
            scrollView!!
            val content = scrollView.getChildAt(0)

            assertTrue(scrollView.isFillViewport)
            assertEquals(scrollView.height, content.height)
            assertFalse(scrollView.canScrollVertically(1))
            assertTrue(fixture.footer.bottom <= scrollView.height)
            assertTrue(fixture.button.height >= fixture.button.minimumHeight)
        } finally {
            fixture.close()
        }
    }

    private fun inflateAndLayout(fontScale: Float, locale: Locale): Fixture {
        val context = createScaledContext(fontScale, locale)
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val activity = controller.get()
        activity.setTheme(R.style.Style_Authentication)

        val activityRoot = LayoutInflater.from(context).inflate(R.layout.activity_signup, null) as ViewGroup
        val toolbar = activityRoot.findViewById<Toolbar>(R.id.toolbar)
        val container = activityRoot.findViewById<FrameLayout>(R.id.fragment_container)
        val fragmentRoot = LayoutInflater.from(context)
            .inflate(R.layout.fragment_signup, container, false) as ViewGroup
        val footer = fragmentRoot.findViewById<TextView>(R.id.text_footer)
        val button = fragmentRoot.findViewById<Button>(R.id.button)
        footer.text = productionFooterText(context)
        container.addView(fragmentRoot)

        SystemBarUtils.applyInsets(activityRoot, toolbar, container)
        val systemBars = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
            .build()
        ViewCompat.dispatchApplyWindowInsets(activityRoot, systemBars)

        activity.setContentView(activityRoot)
        val metrics = context.resources.displayMetrics
        activityRoot.measure(exactly(metrics.widthPixels), exactly(metrics.heightPixels))
        activityRoot.layout(0, 0, activityRoot.measuredWidth, activityRoot.measuredHeight)

        assertEquals(fontScale, context.resources.configuration.fontScale, 0f)
        assertEquals(metrics.widthPixels, activityRoot.width)
        assertEquals(metrics.heightPixels, activityRoot.height)
        assertTrue(footer.textSize > 0f)
        shadowOf(Looper.getMainLooper()).idle()

        return Fixture(controller, fragmentRoot, footer, button)
    }

    @Suppress("DEPRECATION")
    private fun productionFooterText(context: Context): CharSequence {
        val color = Integer.toHexString(Color.BLACK and 0xffffff)
        return Html.fromHtml(
            String.format(
                context.getString(com.simperium.R.string.simperium_footer_signup),
                "<span style=\"color:#",
                color,
                "\">",
                "</span>"
            )
        )
    }

    private fun createScaledContext(fontScale: Float, locale: Locale): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            this.fontScale = fontScale
            setLocale(locale)
        }
        return ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.Style_Authentication)
    }

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private data class Fixture(
        val controller: org.robolectric.android.controller.ActivityController<Activity>,
        val fragmentRoot: ViewGroup,
        val footer: TextView,
        val button: Button
    ) {
        fun close() {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
