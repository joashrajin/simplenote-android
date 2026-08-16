package com.automattic.simplenote.utils

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.automattic.simplenote.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemBarUtilsTest {
    @Test
    fun displayCutoutSafeInsetsAreAppliedToEveryInsetConsumer() {
        val views = createViews()
        SystemBarUtils.applyInsets(views.root, views.toolbar, views.content)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 6, 8, 10))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(12, 18, 16, 20))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 14))
            .build()

        ViewCompat.dispatchApplyWindowInsets(views.root, insets)
        ViewCompat.dispatchApplyWindowInsets(views.toolbar, insets)
        ViewCompat.dispatchApplyWindowInsets(views.content, insets)

        assertEquals(12, views.root.paddingLeft)
        assertEquals(16, views.root.paddingRight)
        assertEquals(18, (views.toolbar.layoutParams as MarginLayoutParams).topMargin)
        assertEquals(20, views.content.paddingBottom)
    }

    @Test
    fun existingBottomPaddingStillWinsWithoutADisplayCutout() {
        val views = createViews(contentBottomPadding = 40)
        SystemBarUtils.applyInsets(
            views.root,
            views.toolbar,
            views.content,
            keepContentBottomPadding = true
        )

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(7, 11, 13, 17))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 30))
            .build()

        ViewCompat.dispatchApplyWindowInsets(views.root, insets)
        ViewCompat.dispatchApplyWindowInsets(views.toolbar, insets)
        ViewCompat.dispatchApplyWindowInsets(views.content, insets)

        assertEquals(7, views.root.paddingLeft)
        assertEquals(13, views.root.paddingRight)
        assertEquals(11, (views.toolbar.layoutParams as MarginLayoutParams).topMargin)
        assertEquals(40, views.content.paddingBottom)
    }

    @Test
    fun imeInsetIsForwardedAfterTheHandledBottomCutout() {
        val views = createViews()
        SystemBarUtils.applyInsets(views.root, views.toolbar, views.content)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, 10))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 0, 20))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 35))
            .build()

        val forwardedInsets = ViewCompat.dispatchApplyWindowInsets(views.content, insets)

        assertEquals(35, views.content.paddingBottom)
        assertEquals(15, forwardedInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
    }

    private fun createViews(contentBottomPadding: Int = 0): InsetViews {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val context = ContextThemeWrapper(application, R.style.Theme_Simplestyle)
        val root = CoordinatorLayout(context)
        val toolbar = Toolbar(context).apply {
            layoutParams = MarginLayoutParams(100, 100)
        }
        val content = View(context).apply {
            setPadding(1, 2, 3, contentBottomPadding)
        }
        root.addView(toolbar)
        root.addView(content)
        return InsetViews(root, toolbar, content)
    }

    private data class InsetViews(
        val root: CoordinatorLayout,
        val toolbar: Toolbar,
        val content: View
    )
}
