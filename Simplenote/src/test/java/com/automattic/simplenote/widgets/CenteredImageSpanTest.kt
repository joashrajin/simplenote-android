package com.automattic.simplenote.widgets

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.style.DynamicDrawableSpan
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the checklist checkbox span metrics. With a paint whose ascent/descent are -29/8 (font
 * height 37) and a 30px-tall drawable, the drawable height used for centering is 30 + 1dp, so the
 * results differ between mdpi (1px) and xhdpi (2px); all halvings are integer divisions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "xhdpi")
class CenteredImageSpanTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun keepsDrawableAndBottomAlignment() {
        val drawable = RecordingDrawable(0, 0, 40, 30)

        val span = CenteredImageSpan(context, drawable)

        assertSame(drawable, span.drawable)
        assertEquals(DynamicDrawableSpan.ALIGN_BOTTOM, span.verticalAlignment)
    }

    @Test
    fun getSizeReturnsBoundsRightWithoutTouchingNullMetrics() {
        val span = CenteredImageSpan(context, RecordingDrawable(0, 0, 40, 30))

        assertEquals(40, span.getSize(fixedPaint(), "x", 0, 1, null))
    }

    @Test
    fun getSizeReturnsBoundsRightNotWidth() {
        val span = CenteredImageSpan(context, RecordingDrawable(5, 2, 45, 32))

        assertEquals(45, span.getSize(fixedPaint(), "x", 0, 1, null))
    }

    @Test
    fun getSizeWithUnsetBoundsIsZero() {
        val span = CenteredImageSpan(context, RecordingDrawable(0, 0, 0, 0))

        assertEquals(0, span.getSize(fixedPaint(), null, 0, 0, null))
    }

    @Test
    fun getSizeCentersMetricsOnFontAtXhdpi() {
        val span = CenteredImageSpan(context, RecordingDrawable(0, 0, 40, 30))
        val metrics = metrics(ascent = 0, descent = 0, top = 0, bottom = 0, leading = 3)

        val size = span.getSize(fixedPaint(), "x", 0, 1, metrics)

        assertEquals(40, size)
        // drHeight = 30 + 2 = 32; centerY = -29 + 37 / 2 = -11.
        assertEquals(-27, metrics.ascent)
        assertEquals(-27, metrics.top)
        assertEquals(5, metrics.descent)
        assertEquals(5, metrics.bottom)
        assertEquals(3, metrics.leading)
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun getSizeCentersMetricsOnFontAtMdpi() {
        val span = CenteredImageSpan(context, RecordingDrawable(0, 0, 40, 30))
        val metrics = metrics(ascent = 0, descent = 0, top = 0, bottom = 0, leading = 0)

        span.getSize(fixedPaint(), "x", 0, 1, metrics)

        // drHeight = 30 + 1 = 31, halved to 15.
        assertEquals(-26, metrics.ascent)
        assertEquals(-26, metrics.top)
        assertEquals(4, metrics.descent)
        assertEquals(4, metrics.bottom)
    }

    @Test
    fun drawTranslatesToCenteredOriginAtXhdpi() {
        val drawable = RecordingDrawable(0, 0, 40, 30)
        val span = CenteredImageSpan(context, drawable)
        val canvas = RecordingCanvas()

        span.draw(canvas, "x", 0, 1, 12f, 70, 100, 110, fixedPaint())

        // centerY = 100 + 8 - 37 / 2 = 90; transY = 90 - 32 / 2 = 74.
        assertEquals(listOf("save", "translate(12.0, 74.0)", "draw", "restore"), canvas.events(drawable))
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun drawTranslatesToCenteredOriginAtMdpi() {
        val drawable = RecordingDrawable(0, 0, 40, 30)
        val span = CenteredImageSpan(context, drawable)
        val canvas = RecordingCanvas()

        span.draw(canvas, "x", 0, 1, 0f, 0, 0, 0, fixedPaint())

        // centerY = 0 + 8 - 18 = -10; transY = -10 - 31 / 2 = -25.
        assertEquals(listOf("save", "translate(0.0, -25.0)", "draw", "restore"), canvas.events(drawable))
    }

    @Test
    fun drawIgnoresTopAndBottom() {
        val drawable = RecordingDrawable(0, 0, 40, 30)
        val span = CenteredImageSpan(context, drawable)
        val canvas = RecordingCanvas()

        span.draw(canvas, null, 0, 0, 12f, -500, 100, 500, fixedPaint())

        assertEquals(listOf("save", "translate(12.0, 74.0)", "draw", "restore"), canvas.events(drawable))
    }

    private fun fixedPaint(): Paint = object : Paint() {
        override fun getFontMetricsInt(): Paint.FontMetricsInt =
            metrics(ascent = -29, descent = 8, top = -31, bottom = 9, leading = 0)
    }

    private fun metrics(ascent: Int, descent: Int, top: Int, bottom: Int, leading: Int) =
        Paint.FontMetricsInt().also {
            it.ascent = ascent
            it.descent = descent
            it.top = top
            it.bottom = bottom
            it.leading = leading
        }

    private class RecordingCanvas : Canvas() {
        val log = mutableListOf<String>()

        override fun save(): Int {
            log += "save"
            return log.size
        }

        override fun translate(dx: Float, dy: Float) {
            log += "translate($dx, $dy)"
        }

        override fun restore() {
            log += "restore"
        }

        fun events(drawable: RecordingDrawable): List<String> {
            assertSame(this, drawable.drawnOn)
            return log
        }
    }

    private class RecordingDrawable(left: Int, top: Int, right: Int, bottom: Int) : Drawable() {
        var drawnOn: Canvas? = null

        init {
            setBounds(left, top, right, bottom)
        }

        override fun draw(canvas: Canvas) {
            drawnOn = canvas
            (canvas as RecordingCanvas).log += "draw"
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
