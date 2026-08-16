package com.automattic.simplenote;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class AboutFragmentStoreLinkTest {
    @Test
    public void storeLinkAttemptsMarketWithoutBrowser() {
        StoreTestActivity activity = Robolectric.buildActivity(StoreTestActivity.class).setup().get();

        activity.findViewById(R.id.about_store).performClick();

        assertEquals(1, activity.startedIntents.size());
        assertEquals(
            "market://details?id=" + activity.getPackageName(),
            activity.startedIntents.get(0).getDataString()
        );
    }

    @Test
    public void storeLinkFallsBackToHttpsWhenMarketUnavailable() {
        StoreTestActivity activity = Robolectric.buildActivity(StoreTestActivity.class).setup().get();
        registerBrowser(activity);
        activity.rejectMarket = true;

        activity.findViewById(R.id.about_store).performClick();

        assertEquals(2, activity.startedIntents.size());
        assertEquals(
            "market://details?id=" + activity.getPackageName(),
            activity.startedIntents.get(0).getDataString()
        );
        assertEquals(
            "https://play.google.com/store/apps/details?id=" + activity.getPackageName(),
            activity.startedIntents.get(1).getDataString()
        );
    }

    private static void registerBrowser(StoreTestActivity activity) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.activityInfo.applicationInfo.packageName = "browser";
        resolveInfo.activityInfo.name = "BrowserActivity";
        Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(
            new Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.simperium_url))),
            resolveInfo
        );
    }

    public static class StoreTestActivity extends AboutActivity {
        private final List<Intent> startedIntents = new ArrayList<>();
        private boolean rejectMarket;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_Simplestyle_About);
            super.onCreate(savedInstanceState);
        }

        @Override
        public void startActivity(Intent intent) {
            recordIntent(intent);
        }

        @Override
        public void startActivity(Intent intent, Bundle options) {
            recordIntent(intent);
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            recordIntent(intent);
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
            recordIntent(intent);
        }

        private void recordIntent(Intent intent) {
            startedIntents.add(intent);
            if (rejectMarket && "market".equals(intent.getData().getScheme())) {
                throw new ActivityNotFoundException();
            }
        }
    }
}
