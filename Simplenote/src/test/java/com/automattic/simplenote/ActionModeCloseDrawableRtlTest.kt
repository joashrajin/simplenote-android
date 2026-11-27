package com.automattic.simplenote

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.drawable.DrawableCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionModeCloseDrawableRtlTest {
    @Test
    fun actionModeCloseDrawableChangesDirectionInRtl() {
        val ltr = render(View.LAYOUT_DIRECTION_LTR)
        val rtl = render(View.LAYOUT_DIRECTION_RTL)

        assertTrue(hasVisiblePixels(ltr))
        assertTrue(hasVisiblePixels(rtl))
        assertFalse(ltr.sameAs(rtl))
    }

    @Test
    fun rtlActionModeCloseDrawableMirrorsLtrPixels() {
        val ltr = render(View.LAYOUT_DIRECTION_LTR)
        val rtl = render(View.LAYOUT_DIRECTION_RTL)

        assertEquals(ltr.width, rtl.width)
        assertEquals(ltr.height, rtl.height)
        repeat(ltr.height) { y ->
            repeat(ltr.width) { x ->
                assertEquals(ltr.getPixel(x, y), rtl.getPixel(ltr.width - x - 1, y))
            }
        }
    }

    private fun render(layoutDirection: Int): Bitmap {
        val drawable = resolveActionModeCloseDrawable().mutate()
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        DrawableCompat.setLayoutDirection(drawable, layoutDirection)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun resolveActionModeCloseDrawable(): Drawable {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Style_Default)
        val attributes = context.obtainStyledAttributes(intArrayOf(R.attr.actionModeCloseDrawable))

        return try {
            val resourceId = attributes.getResourceId(0, 0)
            requireNotNull(AppCompatResources.getDrawable(context, resourceId))
        } finally {
            attributes.recycle()
        }
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
}
