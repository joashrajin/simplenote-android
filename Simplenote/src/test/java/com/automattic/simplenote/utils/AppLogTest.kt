package com.automattic.simplenote.utils

import com.automattic.simplenote.utils.AppLog.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class AppLogTest {
    @Before
    fun clearLog() {
        AppLog.clearForTesting()
    }

    @Test
    fun retainsLatestEntriesAfterRepeatedRotation() {
        for (index in 0 until 150) {
            AppLog.add(Type.ACCOUNT, "rotation-$index")
        }

        val entries = getEntries()

        assertEquals(LOG_MAX, entries.size)
        for (index in 0 until LOG_MAX) {
            assertEquals("rotation-${index + 50}", entries[index])
        }
    }

    @Test
    fun headersSurviveRotationAndAreEmittedFirst() {
        AppLog.addHeader(Type.DEVICE, "device-info")
        AppLog.addHeader(Type.ACCOUNT, "account-info")

        for (index in 0 until LOG_MAX + 50) {
            AppLog.add(Type.ACCOUNT, "rotation-$index")
        }

        val entries = getEntries()

        assertEquals(LOG_MAX + 2, entries.size)
        assertEquals("device-info", entries[0])
        assertEquals("account-info", entries[1])
        assertEquals("rotation-50", entries[2])
    }

    @Test
    fun headerUpdatesReplacePriorHeaderOfSameType() {
        AppLog.addHeader(Type.DEVICE, "device-info")
        AppLog.addHeader(Type.ACCOUNT, "account-info")
        AppLog.addHeader(Type.ACCOUNT, "account-info-updated")

        val entries = getEntries()

        assertEquals(2, entries.size)
        assertEquals("device-info", entries[0])
        assertEquals("account-info-updated", entries[1])
        assertFalse(entries.contains("account-info"))
    }

    @Test(timeout = 10_000)
    fun retainsCompleteUniqueEntriesFromConcurrentWriters() {
        for (index in 0 until LOG_MAX) {
            AppLog.add(Type.ACCOUNT, "seed-$index")
        }

        val writerCount = 4
        val entriesPerWriter = 25
        val readyLatch = CountDownLatch(writerCount + 1)
        val startLatch = CountDownLatch(1)
        val firstWritesDoneLatch = CountDownLatch(writerCount)
        val readerObservedWritesLatch = CountDownLatch(1)
        val writersDoneLatch = CountDownLatch(writerCount)
        val executor = Executors.newFixedThreadPool(writerCount + 1)
        val expectedEntries = HashSet<String>()
        val futures = arrayOfNulls<Future<*>>(writerCount)
        val readerFuture = executor.submit<Void?> {
            readyLatch.countDown()
            startLatch.await()
            firstWritesDoneLatch.await()
            try {
                assertCompleteSnapshot()
            } finally {
                readerObservedWritesLatch.countDown()
            }
            do {
                assertCompleteSnapshot()
            } while (writersDoneLatch.count > 0 && !Thread.currentThread().isInterrupted)
            null
        }

        for (writerIndex in 0 until writerCount) {
            for (entry in 0 until entriesPerWriter) {
                expectedEntries.add("concurrent-$writerIndex-$entry")
            }
            futures[writerIndex] = executor.submit<Void?> {
                readyLatch.countDown()
                startLatch.await()
                try {
                    var entry = 0
                    while (entry < entriesPerWriter && !Thread.currentThread().isInterrupted) {
                        AppLog.add(Type.ACCOUNT, "concurrent-$writerIndex-$entry")
                        if (entry == 0) {
                            firstWritesDoneLatch.countDown()
                            readerObservedWritesLatch.await()
                        }
                        entry++
                    }
                } finally {
                    writersDoneLatch.countDown()
                }
                null
            }
        }

        try {
            assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
            startLatch.countDown()
            for (future in futures) {
                future!!.get(5, TimeUnit.SECONDS)
            }
            readerFuture.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }

        val entries = getEntries()

        assertEquals(LOG_MAX, entries.size)
        assertEquals(entries.size, HashSet(entries).size)
        assertEquals(expectedEntries, HashSet(entries))
    }

    @Test
    fun preservesFormattingForTimestampedAndUntimestampedTypes() {
        AppLog.add(Type.ACCOUNT, "account")
        AppLog.add(Type.DEVICE, "device")
        AppLog.add(Type.SYNC, "sync")

        val entries = getEntries()

        assertEquals(3, entries.size)
        assertEquals("account", entries[0])
        assertEquals("device", entries[1])
        assertTrue(
            entries[2].matches(
                Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} - SYNC: sync")
            )
        )
        assertTrue(AppLog.get().endsWith("\n"))
    }

    private fun getEntries(): List<String> = AppLog.get().split("\n").dropLastWhile { it.isEmpty() }

    private fun assertCompleteSnapshot() {
        val entries = getEntries()
        assertEquals(LOG_MAX, entries.size)
        assertEquals(entries.size, HashSet(entries).size)
    }

    private companion object {
        const val LOG_MAX = 100
    }
}
