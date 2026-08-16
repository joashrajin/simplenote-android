package com.automattic.simplenote.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mockStatic;

import com.automattic.simplenote.Simplenote;
import com.automattic.simplenote.analytics.AnalyticsTracker.Stat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnalyticsTrackerTest {
    private AtomicBoolean mAnalyticsEnabled;
    private MockedStatic<Simplenote> mSimplenoteMock;

    @Before
    public void setUp() throws Exception {
        resetTrackerState();
        mAnalyticsEnabled = new AtomicBoolean(true);
        mSimplenoteMock = mockStatic(Simplenote.class);
        mSimplenoteMock.when(Simplenote::analyticsIsEnabled).thenAnswer(invocation -> mAnalyticsEnabled.get());
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (mSimplenoteMock != null) {
                mSimplenoteMock.close();
            }
        } finally {
            resetTrackerState();
        }
    }

    @Test
    public void appliesDeferredMetadataBeforeEveryTrackOverload() throws Exception {
        assertDeferredMetadata(
                () -> AnalyticsTracker.track(Stat.APPLICATION_OPENED),
                "track3:APPLICATION_OPENED:null:null"
        );
        assertDeferredMetadata(
                () -> AnalyticsTracker.track(Stat.APPLICATION_CLOSED, Collections.singletonMap("key", "value")),
                "track4:APPLICATION_CLOSED:null:null:{key=value}"
        );
        assertDeferredMetadata(
                () -> AnalyticsTracker.track(Stat.USER_SIGNED_IN, "user", "label"),
                "track4:USER_SIGNED_IN:user:label:null"
        );
        assertDeferredMetadata(
                () -> AnalyticsTracker.track(
                        Stat.USER_SIGNED_OUT,
                        "user",
                        "label",
                        Collections.singletonMap("key", "value")
                ),
                "track4:USER_SIGNED_OUT:user:label:{key=value}"
        );
    }

    @Test
    public void disabledOperationsStaySilentAndLatestMetadataWins() {
        RecordingTracker tracker = new RecordingTracker();
        AnalyticsTracker.registerTracker(tracker);
        mAnalyticsEnabled.set(false);

        AnalyticsTracker.refreshMetadata("first@example.com");
        AnalyticsTracker.refreshMetadata("second@example.com");
        AnalyticsTracker.refreshMetadata(null);
        AnalyticsTracker.track(Stat.APPLICATION_OPENED);
        AnalyticsTracker.track(Stat.APPLICATION_CLOSED, Collections.emptyMap());
        AnalyticsTracker.track(Stat.USER_SIGNED_IN, "user", "label");
        AnalyticsTracker.track(Stat.USER_SIGNED_OUT, "user", "label", Collections.emptyMap());
        AnalyticsTracker.flush();

        assertTrue(tracker.getCalls().isEmpty());

        mAnalyticsEnabled.set(true);
        AnalyticsTracker.flush();
        AnalyticsTracker.track(Stat.EDITOR_NOTE_CREATED);

        assertEquals(
                Arrays.asList(
                        "flush",
                        "metadata:null",
                        "track3:EDITOR_NOTE_CREATED:null:null"
                ),
                tracker.getCalls()
        );
    }

    @Test
    public void enabledRefreshReplacesPendingMetadataWithoutRepeatingIt() {
        RecordingTracker tracker = new RecordingTracker();
        AnalyticsTracker.registerTracker(tracker);
        mAnalyticsEnabled.set(false);
        AnalyticsTracker.refreshMetadata("stale@example.com");

        mAnalyticsEnabled.set(true);
        AnalyticsTracker.refreshMetadata("current@example.com");
        AnalyticsTracker.track(Stat.APPLICATION_OPENED);

        assertEquals(
                Arrays.asList(
                        "metadata:current@example.com",
                        "track3:APPLICATION_OPENED:null:null"
                ),
                tracker.getCalls()
        );
    }

    @Test
    public void retainsMetadataUntilATrackerIsRegistered() {
        AnalyticsTracker.refreshMetadata("current@example.com");
        RecordingTracker tracker = new RecordingTracker();

        AnalyticsTracker.registerTracker(tracker);
        assertTrue(tracker.getCalls().isEmpty());
        AnalyticsTracker.track(Stat.APPLICATION_OPENED);

        assertEquals(
                Arrays.asList("metadata:current@example.com", "track3:APPLICATION_OPENED:null:null"),
                tracker.getCalls()
        );
    }

    @Test
    public void retriesMetadataAfterTrackerFailure() {
        FailingTracker tracker = new FailingTracker();
        AnalyticsTracker.registerTracker(tracker);
        mAnalyticsEnabled.set(false);
        AnalyticsTracker.refreshMetadata("current@example.com");
        mAnalyticsEnabled.set(true);

        try {
            AnalyticsTracker.track(Stat.APPLICATION_OPENED);
            fail("Expected the first metadata refresh to fail");
        } catch (IllegalStateException ignored) {
        }

        AnalyticsTracker.track(Stat.APPLICATION_CLOSED);

        assertEquals(
                Arrays.asList(
                        "metadata:failed:current@example.com",
                        "metadata:current@example.com",
                        "track3:APPLICATION_CLOSED:null:null"
                ),
                tracker.getCalls()
        );
    }

    @Test
    public void serializesDeferredMetadataBeforeConcurrentEvents() throws Exception {
        BlockingTracker tracker = new BlockingTracker();
        AnalyticsTracker.registerTracker(tracker);
        mAnalyticsEnabled.set(false);
        AnalyticsTracker.refreshMetadata("current@example.com");
        mAnalyticsEnabled.set(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondCompleted = new CountDownLatch(1);
        Future<?> first = submitEnabledTrack(executor, Stat.APPLICATION_OPENED);

        try {
            assertTrue(tracker.mMetadataStarted.await(2, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                try (MockedStatic<Simplenote> mock = mockStatic(Simplenote.class)) {
                    mock.when(Simplenote::analyticsIsEnabled).thenReturn(true);
                    secondStarted.countDown();
                    AnalyticsTracker.track(Stat.APPLICATION_CLOSED);
                } finally {
                    secondCompleted.countDown();
                }
            });

            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            assertFalse(secondCompleted.await(200, TimeUnit.MILLISECONDS));
            tracker.mContinueMetadata.countDown();
            assertTrue(tracker.mFirstEventStarted.await(2, TimeUnit.SECONDS));
            assertFalse(secondCompleted.await(200, TimeUnit.MILLISECONDS));
            tracker.mContinueFirstEvent.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);

            assertEquals(
                    Arrays.asList(
                            "metadata:start:current@example.com",
                            "metadata:end:current@example.com",
                            "track3:start:" + Stat.APPLICATION_OPENED,
                            "track3:end:" + Stat.APPLICATION_OPENED,
                            "track3:APPLICATION_CLOSED:null:null"
                    ),
                    tracker.getCalls()
            );
        } finally {
            tracker.mContinueMetadata.countDown();
            tracker.mContinueFirstEvent.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    private void assertDeferredMetadata(TrackCall trackCall, String expectedEvent) throws Exception {
        resetTrackerState();
        RecordingTracker tracker = new RecordingTracker();
        AnalyticsTracker.registerTracker(tracker);
        mAnalyticsEnabled.set(false);
        AnalyticsTracker.refreshMetadata("current@example.com");

        assertTrue(tracker.getCalls().isEmpty());

        mAnalyticsEnabled.set(true);
        trackCall.run();

        assertEquals(
                Arrays.asList("metadata:current@example.com", expectedEvent),
                tracker.getCalls()
        );
    }

    private static Future<?> submitEnabledTrack(ExecutorService executor, Stat stat) {
        return executor.submit(() -> {
            try (MockedStatic<Simplenote> mock = mockStatic(Simplenote.class)) {
                mock.when(Simplenote::analyticsIsEnabled).thenReturn(true);
                AnalyticsTracker.track(stat);
            }
        });
    }

    private static void resetTrackerState() throws Exception {
        Field trackersField = AnalyticsTracker.class.getDeclaredField("TRACKERS");
        trackersField.setAccessible(true);
        ((List<?>) trackersField.get(null)).clear();
        setOptionalField("sPendingUsername", null);
        setOptionalField("sHasPendingMetadata", false);
    }

    private static void setOptionalField(String name, Object value) throws Exception {
        try {
            Field field = AnalyticsTracker.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private interface TrackCall {
        void run();
    }

    private static class RecordingTracker implements AnalyticsTracker.Tracker {
        private final List<String> mCalls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void track(Stat stat, String category, String label) {
            mCalls.add("track3:" + stat + ":" + category + ":" + label);
        }

        @Override
        public void track(Stat stat, String category, String label, Map<String, ?> properties) {
            mCalls.add("track4:" + stat + ":" + category + ":" + label + ":" + properties);
        }

        @Override
        public void refreshMetadata(String username) {
            mCalls.add("metadata:" + username);
        }

        @Override
        public void flush() {
            mCalls.add("flush");
        }

        List<String> getCalls() {
            return mCalls;
        }
    }

    private static class BlockingTracker extends RecordingTracker {
        private final CountDownLatch mMetadataStarted = new CountDownLatch(1);
        private final CountDownLatch mContinueMetadata = new CountDownLatch(1);
        private final CountDownLatch mFirstEventStarted = new CountDownLatch(1);
        private final CountDownLatch mContinueFirstEvent = new CountDownLatch(1);

        @Override
        public void track(Stat stat, String category, String label) {
            if (stat != Stat.APPLICATION_OPENED) {
                super.track(stat, category, label);
                return;
            }

            getCalls().add("track3:start:" + stat);
            mFirstEventStarted.countDown();
            await(mContinueFirstEvent);
            getCalls().add("track3:end:" + stat);
        }

        @Override
        public void refreshMetadata(String username) {
            getCalls().add("metadata:start:" + username);
            mMetadataStarted.countDown();
            await(mContinueMetadata);
            getCalls().add("metadata:end:" + username);
        }

        private static void await(CountDownLatch latch) {
            try {
                assertTrue(latch.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
    }

    private static class FailingTracker extends RecordingTracker {
        private boolean mShouldFail = true;

        @Override
        public void refreshMetadata(String username) {
            if (mShouldFail) {
                mShouldFail = false;
                getCalls().add("metadata:failed:" + username);
                throw new IllegalStateException("metadata failure");
            }

            super.refreshMetadata(username);
        }
    }
}
