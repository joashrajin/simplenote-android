package com.automattic.simplenote.utils

import android.app.Application
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowWebView

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], shadows = [BrowserUtilsTest.DestroyTrackingShadowWebView::class])
class BrowserUtilsTest {

    @Before
    fun setUp() {
        DestroyTrackingShadowWebView.destroyCalls = 0
    }

    @Test
    fun webViewAvailabilityProbeIsDestroyed() {
        assertTrue(BrowserUtils.isWebViewInstalled(ApplicationProvider.getApplicationContext()))
        assertEquals(1, DestroyTrackingShadowWebView.destroyCalls)
    }

    @Implements(WebView::class)
    class DestroyTrackingShadowWebView : ShadowWebView() {

        @Implementation
        override fun destroy() {
            destroyCalls++
            super.destroy()
        }

        companion object {
            @JvmStatic
            var destroyCalls: Int = 0
        }
    }
}
