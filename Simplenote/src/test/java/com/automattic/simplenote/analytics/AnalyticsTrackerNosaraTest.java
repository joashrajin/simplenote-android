package com.automattic.simplenote.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.automattic.android.tracks.TracksClient;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AnalyticsTrackerNosaraTest {
    @Test
    public void trackUsesALocaleIndependentEventName() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            Context context = ApplicationProvider.getApplicationContext();
            TracksClient tracksClient = mock(TracksClient.class);
            try (MockedStatic<TracksClient> tracksClients = mockStatic(TracksClient.class)) {
                tracksClients.when(() -> TracksClient.getClient(context)).thenReturn(tracksClient);
                AnalyticsTrackerNosara tracker = new AnalyticsTrackerNosara(context);
                tracker.refreshMetadata("person@example.com");

                tracker.track(AnalyticsTracker.Stat.USER_SIGNED_IN, null, null);

                verify(tracksClient).track(
                        eq("spandroid_user_signed_in"),
                        eq("person@example.com"),
                        eq(TracksClient.NosaraUserType.SIMPLENOTE)
                );
            }
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void trackWithPropertiesUsesALocaleIndependentEventName() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            Context context = ApplicationProvider.getApplicationContext();
            TracksClient tracksClient = mock(TracksClient.class);
            try (MockedStatic<TracksClient> tracksClients = mockStatic(TracksClient.class)) {
                tracksClients.when(() -> TracksClient.getClient(context)).thenReturn(tracksClient);
                AnalyticsTrackerNosara tracker = new AnalyticsTrackerNosara(context);
                tracker.refreshMetadata("person@example.com");

                tracker.track(
                        AnalyticsTracker.Stat.USER_SIGNED_IN,
                        null,
                        null,
                        Collections.singletonMap("source", "test")
                );

                verify(tracksClient).track(
                        eq("spandroid_user_signed_in"),
                        any(JSONObject.class),
                        eq("person@example.com"),
                        eq(TracksClient.NosaraUserType.SIMPLENOTE)
                );
            }
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
