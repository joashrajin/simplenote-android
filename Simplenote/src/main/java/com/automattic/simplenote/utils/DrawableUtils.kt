package com.automattic.simplenote.utils

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat

@Suppress("unused")
object DrawableUtils {
    @JvmStatic
    fun setMenuItemAlpha(menuItem: MenuItem, @FloatRange(from = 0.0, to = 1.0) alpha: Double) {
        val drawable = menuItem.icon

        if (drawable != null) {
            DrawableCompat.wrap(drawable).mutate().alpha = (alpha * 255).toInt() // 255 is 100% opacity.
        }
    }

    @JvmStatic
    fun tintDrawableWithResource(context: Context, @DrawableRes drawableRes: Int, @ColorRes colorRes: Int): Drawable? {
        return tintDrawableWithResource(context, ContextCompat.getDrawable(context, drawableRes), colorRes)
    }

    @JvmStatic
    fun tintDrawable(context: Context, @DrawableRes drawableRes: Int, @ColorInt color: Int): Drawable? {
        return tintDrawable(ContextCompat.getDrawable(context, drawableRes), color)
    }

    @JvmStatic
    fun tintDrawableWithResource(context: Context, drawable: Drawable?, @ColorRes colorRes: Int): Drawable? {
        @ColorInt val tint = ContextCompat.getColor(context, colorRes)
        return tintDrawable(drawable, tint)
    }

    @JvmStatic
    fun tintDrawable(drawable: Drawable?, @ColorInt color: Int): Drawable? {
        if (drawable == null) {
            return null
        }

        val tinted = DrawableCompat.wrap(drawable).mutate()
        DrawableCompat.setTint(tinted, color)
        return tinted
    }

    @JvmStatic
    fun tintDrawableWithAttribute(
        context: Context,
        @DrawableRes drawableRes: Int,
        @AttrRes tintColorAttribute: Int
    ): Drawable? {
        @ColorInt val color = getColor(context, tintColorAttribute)
        return tintDrawable(context, drawableRes, color)
    }

    @JvmStatic
    fun getColor(context: Context, @AttrRes tintColorAttribute: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(tintColorAttribute, typedValue, true)
        return typedValue.data
    }

    @JvmStatic
    fun startAnimatedVectorDrawable(drawable: Drawable?) {
        if (drawable is AnimatedVectorDrawable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            drawable.start()
        } else if (drawable is AnimatedVectorDrawableCompat) {
            drawable.start()
        }
    }

    @JvmStatic
    fun tintMenuWithAttribute(context: Context, menu: Menu, @AttrRes tintColorAttribute: Int) {
        @ColorInt val color = getColor(context, tintColorAttribute)
        tintMenu(menu, color)
    }

    @JvmStatic
    fun tintMenu(menu: Menu, @ColorInt color: Int) {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            tintMenuItem(item, color)
        }
    }

    @JvmStatic
    fun tintMenuItem(menuItem: MenuItem, @ColorInt color: Int) {
        val tinted = tintDrawable(menuItem.icon, color)
        menuItem.setIcon(tinted)
    }

    // Context is nullable because NoteMarkdownFragment passes Fragment.getActivity(); like the Java
    // version, a null context still throws NullPointerException.
    @JvmStatic
    fun tintMenuItemWithAttribute(context: Context?, menuItem: MenuItem, @AttrRes tintColorAttribute: Int) {
        @ColorInt val color = getColor(context!!, tintColorAttribute)
        tintMenuItem(menuItem, color)
    }
}
