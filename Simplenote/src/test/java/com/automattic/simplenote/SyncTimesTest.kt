package com.automattic.simplenote

import android.content.Context
import android.content.SharedPreferences
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Tag
import com.simperium.Simperium
import com.simperium.client.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.Calendar

/**
 * Pins [SyncTimes]: the seeded sync times, the unsynced-key tracking fed by the local queue, which
 * bucket callbacks stamp, drop, or ignore a key and what each tells the listeners, plus the
 * persistence round trip [SimperiumBuckets] wires onto it (the sync_time preferences, one Long of
 * epoch millis per note key).
 */
class SyncTimesTest {
    private val bucket = mock<Bucket<Note>>()

    @Test
    fun seededTimesAreReturnedAndLaterChangesToTheSeedAreIgnored() {
        val seededTime = calendarAt(1_000L)
        val seed = hashMapOf(KEY to seededTime)
        val syncTimes = SyncTimes<Note>(seed)

        seed.remove(KEY)
        seed[OTHER_KEY] = calendarAt(2_000L)

        assertSame(seededTime, syncTimes.getLastSyncTime(KEY))
        assertNull(syncTimes.getLastSyncTime(OTHER_KEY))
    }

    @Test
    fun unknownKeysHaveNoSyncTimeAndReadAsSynced() {
        val syncTimes = SyncTimes<Note>(emptyMap())

        assertNull(syncTimes.getLastSyncTime(KEY))
        assertTrue(syncTimes.isSynced(KEY))
    }

    @Test
    fun syncObjectStampsTheCurrentTimeAndNotifiesAnUpdate() {
        val syncTimes = SyncTimes<Note>(emptyMap())
        val listener = RecordingListener()
        syncTimes.addListener(listener)

        val before = System.currentTimeMillis()
        syncTimes.bucketListener.onSyncObject(bucket, KEY)
        val after = System.currentTimeMillis()

        val stamped = syncTimes.getLastSyncTime(KEY)!!.timeInMillis
        assertTrue(stamped in before..after)
        assertEquals(listOf("update $KEY $stamped true"), listener.events)
    }

    @Test
    fun networkInsertAndModifyStampTheCurrentTimeAndNotifyAnUpdate() {
        listOf(Bucket.ChangeType.INSERT, Bucket.ChangeType.MODIFY).forEach { type ->
            val syncTimes = SyncTimes<Note>(hashMapOf(KEY to calendarAt(1_000L)))
            val listener = RecordingListener()
            syncTimes.addListener(listener)

            val before = System.currentTimeMillis()
            syncTimes.bucketListener.onNetworkChange(bucket, type, KEY)

            val stamped = syncTimes.getLastSyncTime(KEY)!!.timeInMillis
            assertTrue("$type", stamped >= before)
            assertEquals("$type", listOf("update $KEY $stamped true"), listener.events)
        }
    }

    @Test
    fun networkRemoveDropsTheTimeAndNotifiesARemoval() {
        val syncTimes = SyncTimes<Note>(hashMapOf(KEY to calendarAt(1_000L), OTHER_KEY to calendarAt(2_000L)))
        val listener = RecordingListener()
        syncTimes.addListener(listener)

        syncTimes.bucketListener.onNetworkChange(bucket, Bucket.ChangeType.REMOVE, KEY)

        assertNull(syncTimes.getLastSyncTime(KEY))
        assertEquals(2_000L, syncTimes.getLastSyncTime(OTHER_KEY)!!.timeInMillis)
        assertEquals(listOf("remove $KEY"), listener.events)
    }

    @Test
    fun networkChangeWithoutAnEntityIdIsIgnored() {
        val syncTimes = SyncTimes<Note>(hashMapOf(KEY to calendarAt(1_000L)))
        val listener = RecordingListener()
        syncTimes.addListener(listener)

        syncTimes.bucketListener.onNetworkChange(bucket, Bucket.ChangeType.REMOVE, null)
        syncTimes.bucketListener.onNetworkChange(bucket, Bucket.ChangeType.MODIFY, null)

        assertEquals(1_000L, syncTimes.getLastSyncTime(KEY)!!.timeInMillis)
        assertNull(syncTimes.getLastSyncTime(null))
        assertEquals(emptyList<String>(), listener.events)
    }

    @Test
    fun deleteObjectDropsTheObjectsTimeAndNotifiesARemoval() {
        val syncTimes = SyncTimes<Note>(hashMapOf(KEY to calendarAt(1_000L)))
        val listener = RecordingListener()
        syncTimes.addListener(listener)

        syncTimes.bucketListener.onDeleteObject(bucket, Note(KEY))

        assertNull(syncTimes.getLastSyncTime(KEY))
        assertEquals(listOf("remove $KEY"), listener.events)
    }

    @Test
    fun saveAndBeforeUpdateCallbacksChangeNothing() {
        val syncTimes = SyncTimes<Note>(hashMapOf(KEY to calendarAt(1_000L)))
        val listener = RecordingListener()
        syncTimes.addListener(listener)

        syncTimes.bucketListener.onSaveObject(bucket, Note(KEY))
        syncTimes.bucketListener.onBeforeUpdateObject(bucket, Note(KEY))

        assertEquals(1_000L, syncTimes.getLastSyncTime(KEY)!!.timeInMillis)
        assertTrue(syncTimes.isSynced(KEY))
        assertEquals(emptyList<String>(), listener.events)
    }

    @Test
    fun localQueueChangeNotifiesOnlyKeysWhoseSyncedStateFlipped() {
        val syncTimes = SyncTimes<Note>(hashMapOf(KEY to calendarAt(1_000L)))
        val listener = RecordingListener()
        syncTimes.addListener(listener)

        syncTimes.bucketListener.onLocalQueueChange(bucket, setOf(KEY, OTHER_KEY))

        assertFalse(syncTimes.isSynced(KEY))
        assertFalse(syncTimes.isSynced(OTHER_KEY))
        assertEquals(setOf("update $KEY 1000 false", "update $OTHER_KEY null false"), listener.events.toSet())
        assertEquals(2, listener.events.size)

        listener.events.clear()
        syncTimes.bucketListener.onLocalQueueChange(bucket, setOf(OTHER_KEY, THIRD_KEY))

        assertTrue(syncTimes.isSynced(KEY))
        assertFalse(syncTimes.isSynced(OTHER_KEY))
        assertFalse(syncTimes.isSynced(THIRD_KEY))
        assertEquals(setOf("update $KEY 1000 true", "update $THIRD_KEY null false"), listener.events.toSet())
        assertEquals(2, listener.events.size)

        listener.events.clear()
        syncTimes.bucketListener.onLocalQueueChange(bucket, emptySet())

        assertTrue(syncTimes.isSynced(OTHER_KEY))
        assertTrue(syncTimes.isSynced(THIRD_KEY))
        assertEquals(setOf("update $OTHER_KEY null true", "update $THIRD_KEY null true"), listener.events.toSet())
        assertEquals(2, listener.events.size)
    }

    @Test
    fun localQueueChangeDoesNotStampSyncTimes() {
        val syncTimes = SyncTimes<Note>(emptyMap())

        syncTimes.bucketListener.onLocalQueueChange(bucket, setOf(KEY))
        syncTimes.bucketListener.onLocalQueueChange(bucket, emptySet())

        assertNull(syncTimes.getLastSyncTime(KEY))
    }

    @Test
    fun everyDistinctListenerIsNotifiedOnceUntilRemoved() {
        val syncTimes = SyncTimes<Note>(emptyMap())
        val first = RecordingListener()
        val second = RecordingListener()
        syncTimes.addListener(first)
        syncTimes.addListener(first)
        syncTimes.addListener(second)

        syncTimes.bucketListener.onNetworkChange(bucket, Bucket.ChangeType.REMOVE, KEY)

        assertEquals(listOf("remove $KEY"), first.events)
        assertEquals(listOf("remove $KEY"), second.events)

        syncTimes.removeListener(first)
        syncTimes.bucketListener.onNetworkChange(bucket, Bucket.ChangeType.REMOVE, OTHER_KEY)

        assertEquals(listOf("remove $KEY"), first.events)
        assertEquals(listOf("remove $KEY", "remove $OTHER_KEY"), second.events)
    }

    @Test
    fun bucketListenerIsOneStableInstance() {
        val syncTimes = SyncTimes<Note>(emptyMap())

        assertSame(syncTimes.bucketListener, syncTimes.bucketListener)
    }

    @Test
    fun simperiumBucketsRestoresPersistedSyncTimesFromTheSyncTimePreferences() {
        val persistence = Persistence(mapOf(KEY to 1_000L, OTHER_KEY to 2_000L))

        val syncTimes = persistence.buckets().noteSyncTimes

        verify(persistence.context).getSharedPreferences(Simplenote.SYNC_TIME_PREFERENCES, Context.MODE_PRIVATE)
        assertEquals("sync_time", Simplenote.SYNC_TIME_PREFERENCES)
        assertEquals(1_000L, syncTimes.getLastSyncTime(KEY)!!.timeInMillis)
        assertEquals(2_000L, syncTimes.getLastSyncTime(OTHER_KEY)!!.timeInMillis)
    }

    @Test
    fun simperiumBucketsPersistsEachStampAsEpochMillisUnderTheNoteKey() {
        val persistence = Persistence(emptyMap())
        val syncTimes = persistence.buckets().noteSyncTimes

        syncTimes.bucketListener.onSyncObject(bucket, KEY)

        verify(persistence.editor).putLong(KEY, syncTimes.getLastSyncTime(KEY)!!.timeInMillis)
        verify(persistence.editor).apply()
    }

    @Test
    fun simperiumBucketsDeletesThePersistedTimeOnRemoval() {
        val persistence = Persistence(mapOf(KEY to 1_000L))
        val syncTimes = persistence.buckets().noteSyncTimes

        syncTimes.bucketListener.onNetworkChange(bucket, Bucket.ChangeType.REMOVE, KEY)

        verify(persistence.editor).remove(KEY)
        verify(persistence.editor).apply()
    }

    @Test
    fun simperiumBucketsPersistsNothingForAnUnsyncedKeyWithoutATime() {
        val persistence = Persistence(emptyMap())
        val syncTimes = persistence.buckets().noteSyncTimes

        syncTimes.bucketListener.onLocalQueueChange(bucket, setOf(KEY))

        verify(persistence.preferences, never()).edit()
        verifyNoInteractions(persistence.editor)
    }

    @Test
    fun simperiumBucketsRewritesTheKnownTimeWhenTheSyncedStateFlips() {
        val persistence = Persistence(mapOf(KEY to 1_000L))
        val syncTimes = persistence.buckets().noteSyncTimes

        syncTimes.bucketListener.onLocalQueueChange(bucket, setOf(KEY))

        verify(persistence.editor).putLong(eq(KEY), eq(1_000L))
    }

    private class RecordingListener : SyncTimes.SyncTimeListener {
        val events = mutableListOf<String>()

        override fun onRemove(entityId: String?) {
            events += "remove $entityId"
        }

        override fun onUpdate(entityId: String?, lastSyncTime: Calendar?, isSynced: Boolean) {
            events += "update $entityId ${lastSyncTime?.timeInMillis} $isSynced"
        }
    }

    private class Persistence(stored: Map<String, Long>) {
        val editor = mock<SharedPreferences.Editor>()
        val preferences = mock<SharedPreferences> {
            on { all } doReturn stored
            on { edit() } doReturn editor
        }
        val context = mock<Context> {
            on { getSharedPreferences(any(), any()) } doReturn preferences
        }

        init {
            whenever(editor.putLong(anyOrNull(), any())).thenReturn(editor)
            whenever(editor.remove(anyOrNull())).thenReturn(editor)
        }

        fun buckets(): SimperiumBuckets {
            val simperium = mock<Simperium> {
                on { bucket(any<Note.Schema>()) } doReturn mock()
                on { bucket(any<Tag.Schema>()) } doReturn mock()
                on { bucket(any<Preferences.Schema>()) } doReturn mock()
                on { bucket(any<Account.Schema>()) } doReturn mock()
            }
            return SimperiumBuckets(context, simperium)
        }
    }

    private fun calendarAt(timeInMillis: Long): Calendar = Calendar.getInstance().apply {
        this.timeInMillis = timeInMillis
    }

    private companion object {
        const val KEY = "note-1"
        const val OTHER_KEY = "note-2"
        const val THIRD_KEY = "note-3"
    }
}
