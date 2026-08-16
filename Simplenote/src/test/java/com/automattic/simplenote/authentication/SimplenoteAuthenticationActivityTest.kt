package com.automattic.simplenote.authentication

import android.content.Context
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
@Config(sdk = [35])
class SimplenoteAuthenticationActivityTest {
    @Test
    fun authenticationRootUsesTheLargestSystemBarOrCutoutInsetOnEveryEdge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val content = View(context)
        content.setPadding(1, 2, 3, 4)
        val windowInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 8, 12, 16))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(20, 6, 10, 24))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 40))
            .build()

        SimplenoteAuthenticationActivity.applyAuthenticationInsets(content)
        val returnedInsets = ViewCompat.dispatchApplyWindowInsets(content, windowInsets)

        assertEquals(21, content.paddingLeft)
        assertEquals(10, content.paddingTop)
        assertEquals(15, content.paddingRight)
        assertEquals(28, content.paddingBottom)
        assertEquals(Insets.NONE, returnedInsets.getInsets(WindowInsetsCompat.Type.systemBars()))
        assertEquals(Insets.NONE, returnedInsets.getInsets(WindowInsetsCompat.Type.displayCutout()))
        assertEquals(16, returnedInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom)

        val smallerInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(3, 6, 9, 12))
            .build()
        ViewCompat.dispatchApplyWindowInsets(content, smallerInsets)
        assertEquals(4, content.paddingLeft)
        assertEquals(8, content.paddingTop)
        assertEquals(12, content.paddingRight)
        assertEquals(16, content.paddingBottom)
    }

    @Test
    fun authenticationRootAppliesSystemBarInsetsWithoutACutout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val content = View(context)
        val windowInsets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(3, 6, 9, 12))
            .build()

        SimplenoteAuthenticationActivity.applyAuthenticationInsets(content)
        ViewCompat.dispatchApplyWindowInsets(content, windowInsets)

        assertEquals(3, content.paddingLeft)
        assertEquals(6, content.paddingTop)
        assertEquals(9, content.paddingRight)
        assertEquals(12, content.paddingBottom)
    }
}
