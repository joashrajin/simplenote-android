package com.automattic.simplenote.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.automattic.simplenote.utils.AppLog.Type;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AppLogTest {
    private static final int LOG_MAX = 100;

    @Test
    public void retainsLatestEntriesAfterRepeatedRotation() {
        for (int index = 0; index < 150; index++) {
            AppLog.add(Type.ACCOUNT, "rotation-" + index);
        }

        List<String> entries = getEntries();

        assertEquals(LOG_MAX, entries.size());
        for (int index = 0; index < LOG_MAX; index++) {
            assertEquals("rotation-" + (index + 50), entries.get(index));
        }
    }

    @Test(timeout = 10_000)
    public void retainsCompleteUniqueEntriesFromConcurrentWriters() throws Exception {
        for (int index = 0; index < LOG_MAX; index++) {
            AppLog.add(Type.ACCOUNT, "seed-" + index);
        }

        int writerCount = 4;
        int entriesPerWriter = 25;
        CountDownLatch readyLatch = new CountDownLatch(writerCount + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch firstWritesDoneLatch = new CountDownLatch(writerCount);
        CountDownLatch readerObservedWritesLatch = new CountDownLatch(1);
        CountDownLatch writersDoneLatch = new CountDownLatch(writerCount);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount + 1);
        Set<String> expectedEntries = new HashSet<>();
        Future<?>[] futures = new Future<?>[writerCount];
        Future<?> readerFuture = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            firstWritesDoneLatch.await();
            try {
                assertCompleteSnapshot();
            } finally {
                readerObservedWritesLatch.countDown();
            }
            do {
                assertCompleteSnapshot();
            } while (writersDoneLatch.getCount() > 0);
            return null;
        });

        for (int writer = 0; writer < writerCount; writer++) {
            int writerIndex = writer;
            for (int entry = 0; entry < entriesPerWriter; entry++) {
                expectedEntries.add("concurrent-" + writerIndex + "-" + entry);
            }
            futures[writer] = executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    for (int entry = 0; entry < entriesPerWriter; entry++) {
                        AppLog.add(Type.ACCOUNT, "concurrent-" + writerIndex + "-" + entry);
                        if (entry == 0) {
                            firstWritesDoneLatch.countDown();
                            readerObservedWritesLatch.await();
                        }
                    }
                } finally {
                    writersDoneLatch.countDown();
                }
                return null;
            });
        }

        try {
            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            readerFuture.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<String> entries = getEntries();

        assertEquals(LOG_MAX, entries.size());
        assertEquals(entries.size(), new HashSet<>(entries).size());
        assertEquals(expectedEntries, new HashSet<>(entries));
    }

    @Test
    public void preservesFormattingForTimestampedAndUntimestampedTypes() {
        for (int index = 0; index < LOG_MAX; index++) {
            AppLog.add(Type.ACCOUNT, "reset-" + index);
        }
        AppLog.add(Type.ACCOUNT, "account");
        AppLog.add(Type.DEVICE, "device");
        AppLog.add(Type.SYNC, "sync");

        List<String> entries = getEntries();

        assertEquals("account", entries.get(entries.size() - 3));
        assertEquals("device", entries.get(entries.size() - 2));
        assertTrue(entries.get(entries.size() - 1).matches(
                "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} - SYNC: sync"
        ));
        assertTrue(AppLog.get().endsWith("\n"));
    }

    private List<String> getEntries() {
        return Arrays.asList(AppLog.get().split("\n"));
    }

    private void assertCompleteSnapshot() {
        List<String> entries = getEntries();
        assertEquals(LOG_MAX, entries.size());
        assertEquals(entries.size(), new HashSet<>(entries).size());
    }
}
