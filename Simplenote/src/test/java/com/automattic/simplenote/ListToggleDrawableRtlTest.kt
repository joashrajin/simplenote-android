package com.automattic.simplenote

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.util.Locale
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "mdpi")
class ListToggleDrawableRtlTest {
    @Test
    @Config(sdk = [23, 35])
    fun toggleDrawablesResolveMirroredRtlGeometry() {
        val ltrContext = createContext(Locale.ENGLISH)
        val rtlContext = createContext(Locale.forLanguageTag("ar"))

        assertEquals(View.LAYOUT_DIRECTION_LTR, ltrContext.resources.configuration.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_RTL, rtlContext.resources.configuration.layoutDirection)
        assertGeometry(
            R.drawable.av_list_hide_show_24dp,
            ltrContext,
            rtlContext,
            AnimationGeometry(OUTER_LTR, OUTER_LTR, INNER_LTR),
            AnimationGeometry(OUTER_RTL, OUTER_RTL, INNER_RTL)
        )
        assertGeometry(
            R.drawable.av_list_show_hide_24dp,
            ltrContext,
            rtlContext,
            AnimationGeometry(INNER_LTR, INNER_LTR, OUTER_LTR),
            AnimationGeometry(INNER_RTL, INNER_RTL, OUTER_RTL)
        )
    }

    @Test
    @Config(sdk = [35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun showListDrawableRendersAsHorizontalMirrorInRtl() {
        val ltrContext = createContext(Locale.ENGLISH)
        val rtlContext = createContext(Locale.forLanguageTag("ar"))
        val ltr = render(ltrContext, R.drawable.av_list_show_hide_24dp)
        val rtl = render(rtlContext, R.drawable.av_list_show_hide_24dp)

        assertTrue(hasVisiblePixels(ltr))
        assertTrue(hasVisiblePixels(rtl))
        assertFalse(ltr.sameAs(rtl))
        assertEquals(ltr.width, rtl.width)
        assertEquals(ltr.height, rtl.height)
        repeat(ltr.height) { y ->
            repeat(ltr.width) { x ->
                assertColorsClose(ltr.getPixel(x, y), rtl.getPixel(ltr.width - x - 1, y))
            }
        }
    }

    private fun assertGeometry(
        resourceId: Int,
        ltrContext: Context,
        rtlContext: Context,
        expectedLtr: AnimationGeometry,
        expectedRtl: AnimationGeometry
    ) {
        assertFalse(resourcePath(ltrContext, resourceId).contains("/drawable-ldrtl/"))
        assertTrue(resourcePath(rtlContext, resourceId).contains("/drawable-ldrtl/"))
        assertEquals(expectedLtr, readGeometry(ltrContext, resourceId))
        assertEquals(expectedRtl, readGeometry(rtlContext, resourceId))
    }

    private fun render(context: Context, resourceId: Int): Bitmap {
        val drawable = requireNotNull(AppCompatResources.getDrawable(context, resourceId)).mutate()
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun readGeometry(context: Context, resourceId: Int): AnimationGeometry {
        val animatedVector = context.resources.getXml(resourceId)
        val vectorId = findResourceAttribute(animatedVector, "animated-vector", "drawable")
        val animatorId = findResourceAttribute(animatedVector, "target", "animation")
        animatedVector.close()

        val vector = context.resources.getXml(vectorId)
        findElement(vector, "path", "name", "bar")
        val initial = requireNotNull(vector.getAttributeValue(ANDROID_NS, "pathData"))
        vector.close()

        val animator = context.resources.getXml(animatorId)
        findElement(animator, "objectAnimator")
        val from = requireNotNull(animator.getAttributeValue(ANDROID_NS, "valueFrom"))
        val to = requireNotNull(animator.getAttributeValue(ANDROID_NS, "valueTo"))
        animator.close()
        return AnimationGeometry(initial, from, to)
    }

    private fun findResourceAttribute(parser: XmlResourceParser, tag: String, attribute: String): Int {
        findElement(parser, tag)
        return parser.getAttributeResourceValue(ANDROID_NS, attribute, 0).also {
            assertTrue(it != 0)
        }
    }

    private fun findElement(
        parser: XmlResourceParser,
        tag: String,
        attribute: String? = null,
        value: String? = null
    ) {
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == tag &&
                (attribute == null || parser.getAttributeValue(ANDROID_NS, attribute) == value)
            ) {
                return
            }
            parser.next()
        }
        throw AssertionError("Missing $tag element")
    }

    private fun resourcePath(context: Context, resourceId: Int): String {
        val value = TypedValue()
        context.resources.getValue(resourceId, value, true)
        return value.string.toString()
    }

    private fun createContext(locale: Locale): Context {
        val application: Application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return ContextThemeWrapper(
            application.createConfigurationContext(configuration),
            R.style.Style_Default
        )
    }

    private fun hasVisiblePixels(bitmap: Bitmap): Boolean {
        repeat(bitmap.height) { y ->
            repeat(bitmap.width) { x ->
                if (bitmap.getPixel(x, y) ushr 24 != 0) {
                    return true
                }
            }
        }
        return false
    }

    private fun assertColorsClose(expected: Int, actual: Int) {
        assertTrue(abs(Color.alpha(expected) - Color.alpha(actual)) <= COLOR_TOLERANCE)
        assertTrue(abs(Color.red(expected) - Color.red(actual)) <= COLOR_TOLERANCE)
        assertTrue(abs(Color.green(expected) - Color.green(actual)) <= COLOR_TOLERANCE)
        assertTrue(abs(Color.blue(expected) - Color.blue(actual)) <= COLOR_TOLERANCE)
    }

    private data class AnimationGeometry(val initial: String, val from: String, val to: String)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val COLOR_TOLERANCE = 8
        const val OUTER_LTR = "M 2 6 L 4 6 L 4 18 L 2 18 L 2 6 Z"
        const val INNER_LTR = "M 7 6 L 9 6 L 9 18 L 7 18 L 7 6 Z"
        const val OUTER_RTL = "M 20 6 L 22 6 L 22 18 L 20 18 L 20 6 Z"
        const val INNER_RTL = "M 15 6 L 17 6 L 17 18 L 15 18 L 15 6 Z"
    }
}
