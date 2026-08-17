package com.automattic.simplenote.authentication

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.automattic.simplenote.R
import com.automattic.simplenote.utils.HtmlCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-w320dp-h424dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConfirmationLayoutFontScaleTest {
    private lateinit var root: ViewGroup
    private lateinit var message: TextView
    private lateinit var support: TextView

    @Before
    fun setUp() {
        inflateAndLayout(2f)
        assertTrue("Expected the confirmation text to use the requested large font scale", message.textSize > 24f)
    }

    @Test
    fun confirmationMessagesRetainCompleteGeometryAtLargeFontScale() {
        val content = (if (root is ScrollView) root.getChildAt(0) else root) as ViewGroup
        val image = content.getChildAt(0)

        assertTrue(
            "Expected complete content geometry; imageTop=${image.top}, " +
                "messageHeight=${message.measuredHeight}, messageLayoutHeight=${message.layout.height}, " +
                "supportHeight=${support.measuredHeight}, supportLayoutHeight=${support.layout.height}",
            image.top >= 0 &&
                message.measuredHeight >= message.layout.height &&
                support.measuredHeight >= support.layout.height
        )
    }

    @Test
    fun fittingContentRemainsCenteredWithoutScrolling() {
        inflateAndLayout(1f)
        assertTrue("Expected the confirmation layout to be scrollable when needed", root is ScrollView)
        val scrollView = root as ScrollView
        val content = scrollView.getChildAt(0) as ViewGroup

        assertTrue(scrollView.isFillViewport)
        assertEquals(scrollView.height, content.height)
        assertFalse(scrollView.canScrollVertically(1))
        assertTrue(content.getChildAt(0).top > 0)
    }

    @Test
    fun supportMessageCanBeScrolledFullyIntoView() {
        assertTrue("Expected the confirmation layout to be scrollable", root is ScrollView)
        val scrollView = root as ScrollView
        val content = scrollView.getChildAt(0)

        assertTrue(scrollView.isFillViewport)
        assertTrue("Expected confirmation content to exceed the compact viewport", content.height > scrollView.height)

        val maximumScroll = content.height - scrollView.height
        scrollView.scrollTo(0, maximumScroll)

        assertTrue(scrollView.scrollY > 0)
        assertTrue(support.top >= scrollView.scrollY)
        assertTrue(support.bottom <= scrollView.scrollY + scrollView.height)
    }

    private fun inflateAndLayout(fontScale: Float) {
        val context = createScaledContext(fontScale)
        root = LayoutInflater.from(context).inflate(R.layout.fragment_confirmation, null) as ViewGroup
        message = root.findViewById(R.id.email_confirmation_text)
        support = root.findViewById(R.id.support_text)

        populateConfirmationText(context)

        val metrics = context.resources.displayMetrics
        root.measure(exactly(metrics.widthPixels), exactly(metrics.heightPixels))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        assertEquals(320, root.measuredWidth)
        assertEquals(424, root.measuredHeight)
    }

    private fun createScaledContext(fontScale: Float): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            this.fontScale = fontScale
        }
        val scaledContext = application.createConfigurationContext(configuration)
        assertEquals(fontScale, scaledContext.resources.configuration.fontScale, 0f)
        return ContextThemeWrapper(scaledContext, R.style.Style_Authentication)
    }

    private fun populateConfirmationText(context: Context) {
        val email = "<b>extraordinarily.long.account.address@example.com</b>"
        message.text = HtmlCompat.fromHtml(
            String.format(context.getString(R.string.email_confirmation_text), email)
        )

        val supportEmail = context.getString(R.string.support_email)
        val supportLink = "<a href='mailto:$supportEmail'>$supportEmail</a>"
        support.text = HtmlCompat.fromHtml(
            String.format(context.getString(R.string.support_text), supportLink)
        )
        support.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }
}
