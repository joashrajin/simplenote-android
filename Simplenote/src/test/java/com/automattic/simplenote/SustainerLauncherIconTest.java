package com.automattic.simplenote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SustainerLauncherIconTest {
    @Test
    @SdkSuppress(minSdkVersion = 33)
    public void greenLauncherIconUsesSharedMonochromeArtwork() {
        Context context = RuntimeEnvironment.getApplication();
        Drawable drawable = context.getDrawable(R.mipmap.ic_launcher_green);

        assertTrue(drawable instanceof AdaptiveIconDrawable);
        Drawable monochrome = ((AdaptiveIconDrawable) drawable).getMonochrome();
        assertNotNull(monochrome);
        assertEquals(R.drawable.ic_launcher_mono, Shadows.shadowOf(monochrome).getCreatedFromResId());
    }
}
