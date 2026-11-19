package com.automattic.simplenote.utils

import android.app.Application
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SystemBarUtilsTest {
    private val view = View(ApplicationProvider.getApplicationContext()).apply { setPadding(1, 2, 3, 4) }

    @Test
    fun systemBarAndCutoutPaddingAddsTheLargerInsetOnEveryEdgeToTheInitialPadding() {
        SystemBarUtils.applySystemBarAndCutoutPadding(view)

        val remaining = ViewCompat.dispatchApplyWindowInsets(
            view,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 8, 12, 16))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(20, 6, 10, 24))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 40))
                .build()
        )

        assertEquals(listOf(21, 10, 15, 28), view.paddings())
        assertEquals(Insets.NONE, remaining.getInsets(WindowInsetsCompat.Type.systemBars()))
        assertEquals(Insets.NONE, remaining.getInsets(WindowInsetsCompat.Type.displayCutout()))
        assertEquals(16, remaining.getInsets(WindowInsetsCompat.Type.ime()).bottom)
    }

    @Test
    fun systemBarAndCutoutPaddingDoesNotAccumulateAcrossDispatches() {
        SystemBarUtils.applySystemBarAndCutoutPadding(view)
        ViewCompat.dispatchApplyWindowInsets(view, systemBars(Insets.of(20, 20, 20, 20)))

        ViewCompat.dispatchApplyWindowInsets(view, systemBars(Insets.of(3, 6, 9, 12)))

        assertEquals(listOf(4, 8, 12, 16), view.paddings())
    }

    private fun systemBars(insets: Insets) =
        WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), insets).build()

    private fun View.paddings() = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
}
