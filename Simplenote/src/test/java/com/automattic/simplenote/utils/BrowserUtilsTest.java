package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.webkit.WebView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowWebView;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = BrowserUtilsTest.DestroyTrackingShadowWebView.class)
public class BrowserUtilsTest {
    @Before
    public void setUp() {
        DestroyTrackingShadowWebView.destroyCalls = 0;
    }

    @Test
    public void webViewAvailabilityProbeIsDestroyed() {
        assertTrue(BrowserUtils.isWebViewInstalled(RuntimeEnvironment.getApplication()));
        assertEquals(1, DestroyTrackingShadowWebView.destroyCalls);
    }

    @Implements(WebView.class)
    public static class DestroyTrackingShadowWebView extends ShadowWebView {
        private static int destroyCalls;

        @Implementation
        @Override
        protected void destroy() {
            destroyCalls++;
            super.destroy();
        }
    }
}
