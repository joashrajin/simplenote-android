package com.automattic.simplenote

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w320dp-h480dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WordPressPostingStatusLayoutTest {
    @Test
    fun postingStatusWrapsInGermanAtLargeText() {
        val fixture = inflatePostingStatus(Locale.GERMAN, 2f)
        val layout = fixture.status.layout
        val progress = fixture.posting.getChildAt(0)
        val ellipsisCount = (0 until layout.lineCount).sumOf(layout::getEllipsisCount)

        assertEquals(fixture.context.getString(R.string.uploading_post), fixture.status.text.toString())
        assertTrue("Expected the requested large font scale", fixture.status.textSize > 24f)
        assertTrue(
            "Expected the complete posting status to wrap; lines=${layout.lineCount}, " +
                "ellipsisCount=$ellipsisCount, text=${fixture.status.text}",
            layout.lineCount > 1 && ellipsisCount == 0
        )
        assertTrue(fixture.status.measuredHeight >= layout.height)
        assertTrue(fixture.status.measuredHeight > progress.measuredHeight)
        assertTrue(fixture.status.top >= fixture.posting.paddingTop)
        assertTrue(fixture.status.bottom <= fixture.posting.height - fixture.posting.paddingBottom)
        assertTrue(fixture.status.right <= fixture.posting.width - fixture.posting.paddingRight)
    }

    @Test
    fun postingStatusRemainsSingleLineAtDefaultEnglishTextSize() {
        val fixture = inflatePostingStatus(Locale.ENGLISH, 1f)
        val layout = fixture.status.layout

        assertEquals(1, layout.lineCount)
        assertEquals(0, layout.getEllipsisCount(0))
        assertTrue(fixture.status.right <= fixture.posting.width - fixture.posting.paddingRight)
    }

    private fun inflatePostingStatus(locale: Locale, fontScale: Float): Fixture {
        val context = createContext(locale, fontScale)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_wordpress_post, null) as ViewGroup
        val connect = root.findViewById<View>(R.id.wp_dialog_section_connect)
        val posting = root.findViewById<ViewGroup>(R.id.wp_dialog_section_posting)

        root.findViewById<View>(R.id.wp_dialog_section_fields).visibility = View.GONE
        root.findViewById<View>(R.id.wp_dialog_section_success).visibility = View.GONE
        connect.visibility = View.VISIBLE
        posting.visibility = View.GONE
        measureAndLayout(root)

        connect.visibility = View.GONE
        posting.visibility = View.VISIBLE
        measureAndLayout(root)

        assertEquals(320, root.measuredWidth)
        return Fixture(context, posting, posting.getChildAt(1) as TextView)
    }

    private fun createContext(locale: Locale, fontScale: Float): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            this.fontScale = fontScale
            setLocale(locale)
        }
        val context = application.createConfigurationContext(configuration)
        return ContextThemeWrapper(context, R.style.Theme_Simplestyle)
    }

    private fun measureAndLayout(root: View) {
        root.measure(exactly(320), atMost(480))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }

    private fun atMost(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)
    }

    private data class Fixture(
        val context: Context,
        val posting: ViewGroup,
        val status: TextView
    )
}
