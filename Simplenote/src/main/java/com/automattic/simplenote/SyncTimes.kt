package com.automattic.simplenote

import android.util.Log
import com.simperium.client.Bucket
import com.simperium.client.Syncable
import java.util.Calendar

class SyncTimes<T : Syncable>(syncTimes: Map<String, Calendar>) {
    private val lastSyncTimes = HashMap<String?, Calendar>()
    private val unsyncedKeys = HashSet<String?>()
    private val listeners = HashSet<SyncTimeListener>()

    init {
        lastSyncTimes.putAll(syncTimes)
    }

    fun getLastSyncTime(key: String?): Calendar? = lastSyncTimes[key]

    fun isSynced(key: String?): Boolean = !unsyncedKeys.contains(key)

    fun addListener(listener: SyncTimeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SyncTimeListener) {
        listeners.remove(listener)
    }

    private fun notifyUpdate(entityId: String?) {
        for (listener in listeners) {
            listener.onUpdate(entityId, getLastSyncTime(entityId), isSynced(entityId))
        }
    }

    private fun notifyRemove(entityId: String?) {
        for (listener in listeners) {
            listener.onRemove(entityId)
        }
    }

    private fun updateSyncTime(entityId: String?) {
        val now = Calendar.getInstance()
        lastSyncTimes[entityId] = now

        Log.d(TAG, "updateSyncTime: $entityId (${now.time})")
    }

    @JvmField
    var bucketListener: Bucket.Listener<T> = object : Bucket.Listener<T> {
        override fun onBeforeUpdateObject(bucket: Bucket<T>?, syncable: T?) {
        }

        override fun onDeleteObject(bucket: Bucket<T>?, syncable: T) {
            lastSyncTimes.remove(syncable.simperiumKey)
            notifyRemove(syncable.simperiumKey)
        }

        override fun onLocalQueueChange(bucket: Bucket<T>?, entityIds: Set<String?>) {
            val changed = HashSet<String?>()

            for (entityId in unsyncedKeys) {
                if (!entityIds.contains(entityId)) {
                    changed.add(entityId)
                }
            }

            for (entityId in entityIds) {
                if (!unsyncedKeys.contains(entityId)) {
                    changed.add(entityId)
                }
            }

            unsyncedKeys.clear()
            unsyncedKeys.addAll(entityIds)

            for (entityId in changed) {
                Log.d(TAG, "updateIsSynced: $entityId (${isSynced(entityId)})")
                notifyUpdate(entityId)
            }
        }

        override fun onNetworkChange(bucket: Bucket<T>?, type: Bucket.ChangeType?, entityId: String?) {
            if (entityId == null) {
                return
            }

            if (type == Bucket.ChangeType.REMOVE) {
                lastSyncTimes.remove(entityId)
                notifyRemove(entityId)
            } else {
                updateSyncTime(entityId)
                notifyUpdate(entityId)
            }
        }

        override fun onSaveObject(bucket: Bucket<T>?, syncable: T?) {
        }

        override fun onSyncObject(bucket: Bucket<T>?, noteId: String?) {
            updateSyncTime(noteId)
            notifyUpdate(noteId)
        }
    }

    interface SyncTimeListener {
        fun onRemove(entityId: String?)
        fun onUpdate(entityId: String?, lastSyncTime: Calendar?, isSynced: Boolean)
    }

    companion object {
        private val TAG: String = SyncTimes::class.java.simpleName
    }
}
