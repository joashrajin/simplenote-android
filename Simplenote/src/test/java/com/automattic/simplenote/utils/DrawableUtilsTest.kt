package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import androidx.test.core.app.ApplicationProvider
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins DrawableUtils: tinting returns the same (mutated) drawable instance with the tint applied,
 * passes null drawables through, resolves colors from resources and theme attributes, and the
 * menu helpers tint/alpha each item's icon in place. Resource tints are checked on the rendered
 * pixels, so the native graphics runtime is required.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class DrawableUtilsTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val themed: Context = ContextThemeWrapper(app, ThemeUtils.getStyle(app))

    @Test
    fun tintDrawableReturnsNullForNullDrawable() {
        assertNull(DrawableUtils.tintDrawable(null, Color.RED))
    }

    @Test
    fun tintDrawableTintsAndReturnsSameInstance() {
        val drawable = RecordingDrawable()

        val result = DrawableUtils.tintDrawable(drawable, TINT)

        assertSame(drawable, result)
        assertEquals(TINT, drawable.tint!!.defaultColor)
    }

    @Test
    fun tintDrawableFromResourceTintsRenderedPixels() {
        val result = DrawableUtils.tintDrawable(app, R.drawable.ic_cross_24dp, TINT)

        assertOpaquePixelsAre(TINT, result!!)
    }

    @Test
    fun tintDrawableFromResourceMutatesSoOtherInstancesStayUntinted() {
        DrawableUtils.tintDrawable(app, R.drawable.ic_cross_24dp, TINT)

        assertOpaquePixelsAre(Color.WHITE, app.getDrawable(R.drawable.ic_cross_24dp)!!)
    }

    @Test
    fun tintDrawableWithResourceIdsResolvesColorResource() {
        val result = DrawableUtils.tintDrawableWithResource(app, R.drawable.ic_cross_24dp, R.color.blue_50)

        assertOpaquePixelsAre(app.getColor(R.color.blue_50), result!!)
    }

    @Test
    fun tintDrawableWithResourceOnDrawableResolvesColorResource() {
        val drawable = RecordingDrawable()

        val result = DrawableUtils.tintDrawableWithResource(app, drawable, R.color.red_50)

        assertSame(drawable, result)
        assertEquals(app.getColor(R.color.red_50), drawable.tint!!.defaultColor)
    }

    @Test
    fun tintDrawableWithResourceOnNullDrawableReturnsNull() {
        assertNull(DrawableUtils.tintDrawableWithResource(app, null as Drawable?, R.color.red_50))
    }

    @Test
    fun tintDrawableWithAttributeResolvesThemeAttribute() {
        val expected = resolveAttribute(themed, R.attr.colorPrimary)

        val result = DrawableUtils.tintDrawableWithAttribute(themed, R.drawable.ic_cross_24dp, R.attr.colorPrimary)

        assertOpaquePixelsAre(expected, result!!)
    }

    @Test
    fun getColorReturnsResolvedThemeAttributeData() {
        assertEquals(
            resolveAttribute(themed, R.attr.toolbarIconColor),
            DrawableUtils.getColor(themed, R.attr.toolbarIconColor)
        )
        assertEquals(
            ThemeUtils.getColorFromAttribute(themed, R.attr.colorAccent),
            DrawableUtils.getColor(themed, R.attr.colorAccent)
        )
    }

    @Test
    fun getColorReturnsZeroForUnresolvedAttribute() {
        val frameworkThemed = ContextThemeWrapper(app, android.R.style.Theme)

        assertEquals(0, DrawableUtils.getColor(frameworkThemed, R.attr.toolbarIconColor))
    }

    @Test
    fun startAnimatedVectorDrawableIgnoresNullAndPlainDrawables() {
        DrawableUtils.startAnimatedVectorDrawable(null)
        DrawableUtils.startAnimatedVectorDrawable(RecordingDrawable())
    }

    @Test
    fun startAnimatedVectorDrawableStartsFrameworkAnimatedVector() {
        val drawable = mock<AnimatedVectorDrawable>()

        DrawableUtils.startAnimatedVectorDrawable(drawable)

        verify(drawable).start()
    }

    @Test
    fun startAnimatedVectorDrawableStartsCompatAnimatedVector() {
        val drawable = mock<AnimatedVectorDrawableCompat>()

        DrawableUtils.startAnimatedVectorDrawable(drawable)

        verify(drawable).start()
    }

    @Test
    fun setMenuItemAlphaSetsAlphaOnIconInPlace() {
        val icon = RecordingDrawable()
        val item = menuItem(icon)

        DrawableUtils.setMenuItemAlpha(item, 0.3)

        assertEquals(76, icon.alphaValue)
        verify(item, never()).setIcon(any<Drawable>())
    }

    @Test
    fun setMenuItemAlphaBounds() {
        val icon = RecordingDrawable()
        val item = menuItem(icon)

        DrawableUtils.setMenuItemAlpha(item, 1.0)
        assertEquals(255, icon.alphaValue)

        DrawableUtils.setMenuItemAlpha(item, 0.0)
        assertEquals(0, icon.alphaValue)
    }

    @Test
    fun setMenuItemAlphaIgnoresMissingIcon() {
        DrawableUtils.setMenuItemAlpha(menuItem(null), 0.3)
    }

    @Test
    fun tintMenuItemSetsTintedIcon() {
        val icon = RecordingDrawable()
        val item = menuItem(icon)

        DrawableUtils.tintMenuItem(item, TINT)

        assertEquals(TINT, icon.tint!!.defaultColor)
        verify(item).setIcon(icon)
    }

    @Test
    fun tintMenuItemWithoutIconSetsNullIcon() {
        val item = menuItem(null)

        DrawableUtils.tintMenuItem(item, TINT)

        verify(item).setIcon(null as Drawable?)
    }

    @Test
    fun tintMenuItemWithAttributeResolvesThemeAttribute() {
        val icon = RecordingDrawable()
        val item = menuItem(icon)

        DrawableUtils.tintMenuItemWithAttribute(themed, item, R.attr.toolbarIconColor)

        assertEquals(resolveAttribute(themed, R.attr.toolbarIconColor), icon.tint!!.defaultColor)
        verify(item).setIcon(icon)
    }

    @Test
    fun tintMenuTintsEveryItem() {
        val icons = listOf(RecordingDrawable(), RecordingDrawable())
        val items = icons.map { menuItem(it) }
        val menu = menu(items)

        DrawableUtils.tintMenu(menu, TINT)

        icons.forEach { assertEquals(TINT, it.tint!!.defaultColor) }
        items.forEachIndexed { index, item -> verify(item).setIcon(icons[index]) }
    }

    @Test
    fun tintMenuWithAttributeResolvesThemeAttribute() {
        val icon = RecordingDrawable()
        val item = menuItem(icon)

        DrawableUtils.tintMenuWithAttribute(themed, menu(listOf(item)), R.attr.colorPrimary)

        assertEquals(resolveAttribute(themed, R.attr.colorPrimary), icon.tint!!.defaultColor)
        verify(item).setIcon(icon)
    }

    @Test
    fun tintMenuOnEmptyMenuIsNoOp() {
        DrawableUtils.tintMenu(menu(emptyList()), TINT)
    }

    private fun menuItem(icon: Drawable?): MenuItem = mock<MenuItem>().also { whenever(it.icon).thenReturn(icon) }

    private fun menu(items: List<MenuItem>): Menu = mock<Menu>().also { menu ->
        whenever(menu.size()).thenReturn(items.size)
        items.forEachIndexed { index, item -> whenever(menu.getItem(index)).thenReturn(item) }
    }

    private fun resolveAttribute(context: Context, attr: Int): Int {
        val value = TypedValue()
        assertTrue(context.theme.resolveAttribute(attr, value, true))
        return value.data
    }

    private fun assertOpaquePixelsAre(expected: Int, drawable: Drawable) {
        assertNotNull(drawable)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(bitmap))
        var opaque = 0
        for (x in 0 until SIZE) {
            for (y in 0 until SIZE) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) == 255) {
                    opaque++
                    assertEquals("pixel ($x, $y)", expected or OPAQUE, pixel)
                }
            }
        }
        assertTrue("no opaque pixels rendered", opaque > 0)
    }

    private class RecordingDrawable : Drawable() {
        var tint: ColorStateList? = null
        var alphaValue: Int = -1

        override fun setTintList(tint: ColorStateList?) {
            this.tint = tint
        }

        override fun setAlpha(alpha: Int) {
            alphaValue = alpha
        }

        override fun draw(canvas: Canvas) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val SIZE = 48
        const val TINT = 0xFF336699.toInt()
        const val OPAQUE = 0xFF000000.toInt()
    }
}
