package com.automattic.simplenote.utils

import android.content.Context
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import android.widget.RelativeLayout
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DisplayUtilsTest {
    @Test
    fun floatingActionButtonUsesTheLargerBottomDisplayCutoutInset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = View(context)
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 0, 0, 10))
            .setInsets(WindowInsets.Type.displayCutout(), Insets.of(0, 0, 0, 20))
            .build()

        val returnedInsets = DisplayUtils.applyWindowInsetsForFloatingActionButton(
            insets,
            context.resources,
            view
        )

        val params = view.layoutParams as RelativeLayout.LayoutParams
        val margin = context.resources.getDimensionPixelSize(R.dimen.margin_default)
        val button = context.resources.getDimensionPixelSize(R.dimen.button_floating)
        assertSame(insets, returnedInsets)
        assertEquals(margin + 20, params.bottomMargin)
        assertEquals(button, params.width)
        assertEquals(button, params.height)
        assertEquals(RelativeLayout.TRUE, params.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM))
        params.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR)
        assertEquals(RelativeLayout.TRUE, params.getRule(RelativeLayout.ALIGN_PARENT_RIGHT))
    }

    @Test
    fun floatingActionButtonPreservesSystemBarMarginWithoutACutout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = View(context)
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 0, 0, 10))
            .build()

        DisplayUtils.applyWindowInsetsForFloatingActionButton(insets, context.resources, view)

        val params = view.layoutParams as RelativeLayout.LayoutParams
        val margin = context.resources.getDimensionPixelSize(R.dimen.margin_default)
        assertEquals(margin + 10, params.bottomMargin)
    }

    @Test
    @Config(sdk = [29])
    @Suppress("DEPRECATION")
    fun floatingActionButtonPreservesTheLegacySystemWindowBottomInset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = View(context)
        val insets = WindowInsets.Builder()
            .setSystemWindowInsets(Insets.of(0, 0, 0, 30))
            .setStableInsets(Insets.of(0, 0, 0, 10))
            .build()

        DisplayUtils.applyWindowInsetsForFloatingActionButton(insets, context.resources, view)

        val params = view.layoutParams as RelativeLayout.LayoutParams
        val margin = context.resources.getDimensionPixelSize(R.dimen.margin_default)
        assertEquals(margin + 30, params.bottomMargin)
    }
}
