package com.automattic.simplenote

import android.content.Context
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.NoteCountIndexer
import com.automattic.simplenote.models.NoteTagger
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Tag
import com.simperium.Simperium
import com.simperium.client.Bucket
import com.simperium.client.BucketNameInvalid
import java.util.Calendar

/**
 * Owns the Simperium bucket graph. One constructor reproduces the wiring order the buckets had in
 * [Simplenote.onCreate], which the cross-bucket listeners make order-sensitive — the notes bucket
 * must exist before the tag schema indexes it, and the NoteTagger can only be registered once the
 * tags bucket exists — so the bidirectional notes/tags wiring can never be split across providers.
 * Construction must happen on the main thread before any activity, worker, or widget code runs;
 * Hilt guarantees this by injecting the holder into [Simplenote] at the top of its onCreate.
 */
class SimperiumBuckets(context: Context, simperium: Simperium) {
    val noteSyncTimes: SyncTimes<Note>
    val notes: Bucket<Note>
    val tags: Bucket<Tag>
    val preferences: Bucket<Preferences>
    val account: Bucket<Account>

    init {
        val syncTimePersister = SyncTimePersister(context)
        noteSyncTimes = SyncTimes(syncTimePersister.load())
        noteSyncTimes.addListener(syncTimePersister)

        try {
            notes = simperium.bucket(Note.Schema())
            notes.addListener(noteSyncTimes.bucketListener)
            val tagSchema = Tag.Schema()
            tagSchema.addIndex(NoteCountIndexer(notes))
            tags = simperium.bucket(tagSchema)
            preferences = simperium.bucket(Preferences.Schema())
            account = simperium.bucket(Account.Schema())
            // Every time a note changes or is deleted we need to reindex the tag counts
            notes.addListener(NoteTagger(tags))
        } catch (e: BucketNameInvalid) {
            throw RuntimeException("Could not create bucket", e)
        }
    }

    /** The note sync-time persistence, moved verbatim from the Simplenote.SyncTimePersister inner class. */
    private class SyncTimePersister(context: Context) : SyncTimes.SyncTimeListener {
        private val preferences =
            context.getSharedPreferences(Simplenote.SYNC_TIME_PREFERENCES, Context.MODE_PRIVATE)

        fun load(): HashMap<String, Calendar> {
            val syncTimes = HashMap<String, Calendar>()

            @Suppress("UNCHECKED_CAST")
            for ((entityId, timeInMillis) in preferences.all as Map<String, Long>) {
                val instant = Calendar.getInstance()
                instant.timeInMillis = timeInMillis
                syncTimes[entityId] = instant
            }

            return syncTimes
        }

        override fun onRemove(entityId: String?) {
            preferences.edit().remove(entityId).apply()
        }

        override fun onUpdate(entityId: String?, lastSyncTime: Calendar?, isSynced: Boolean) {
            if (lastSyncTime != null) {
                preferences.edit().putLong(entityId, lastSyncTime.timeInMillis).apply()
            }
        }
    }
}
