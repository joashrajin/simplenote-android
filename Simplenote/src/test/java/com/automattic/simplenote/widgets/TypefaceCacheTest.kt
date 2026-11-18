package com.automattic.simplenote.widgets

import android.app.Application
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the font cache and the Roboto text views built on it. Robolectric creates a new Typeface on
 * every createFromAsset call, so identity checks show whether a value came from the cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TypefaceCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun typefaceNamesMatchTheBundledAssets() {
        assertEquals("Roboto-Regular.ttf", TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR)
        assertEquals("Roboto-Medium.ttf", TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM)
        val fonts = context.assets.list("fonts").orEmpty().toList()
        assertEquals(true, fonts.contains(TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR))
        assertEquals(true, fonts.contains(TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM))
    }

    @Test
    fun getTypefaceReturnsNullForNullArguments() {
        assertNull(TypefaceCache.getTypeface(null, TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR))
        assertNull(TypefaceCache.getTypeface(context, null))
        assertNull(TypefaceCache.getTypeface(null, null))
    }

    @Test
    fun getTypefaceCachesOneInstancePerName() {
        val regular = TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR)
        val medium = TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM)

        assertNotNull(regular)
        assertNotNull(medium)
        assertNotSame(regular, medium)
        assertSame(regular, TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR))
        assertSame(medium, TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM))
    }

    @Test
    fun getTypefaceSharesTheCacheAcrossContexts() {
        val viaApplication = TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR)
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()

        assertSame(viaApplication, TypefaceCache.getTypeface(activity, TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR))
    }

    @Test
    fun setCustomTypefaceAppliesTheCachedTypeface() {
        val view = TextView(context)

        TypefaceCache.setCustomTypeface(context, view, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM)

        assertSame(TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM), view.typeface)
    }

    @Test
    fun setCustomTypefaceIgnoresNullArguments() {
        val view = TextView(context)
        val original = view.typeface

        TypefaceCache.setCustomTypeface(null, view, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM)
        TypefaceCache.setCustomTypeface(context, null, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM)
        TypefaceCache.setCustomTypeface(context, view, null)

        assertSame(original, view.typeface)
    }

    @Test
    fun setCustomTypefaceSkipsViewsInEditMode() {
        val view = object : TextView(context) {
            override fun isInEditMode() = true
        }
        val original = view.typeface

        TypefaceCache.setCustomTypeface(context, view, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM)

        assertSame(original, view.typeface)
    }

    @Test
    fun robotoMediumTextViewAppliesMediumFromEveryConstructor() {
        val medium = TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM)
        val attrs = Robolectric.buildAttributeSet().build()

        assertSame(medium, RobotoMediumTextView(context).typeface)
        assertSame(medium, RobotoMediumTextView(context, attrs).typeface)
        assertSame(medium, RobotoMediumTextView(context, null).typeface)
        assertSame(medium, RobotoMediumTextView(context, attrs, 0).typeface)
    }

    @Test
    fun robotoRegularTextViewAppliesRegularFromEveryConstructor() {
        val regular = TypefaceCache.getTypeface(context, TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR)
        val attrs = Robolectric.buildAttributeSet().build()

        assertSame(regular, RobotoRegularTextView(context).typeface)
        assertSame(regular, RobotoRegularTextView(context, attrs).typeface)
        assertSame(regular, RobotoRegularTextView(context, null).typeface)
        assertSame(regular, RobotoRegularTextView(context, attrs, 0).typeface)
    }
}
