package com.automattic.simplenote.repositories

import android.content.SharedPreferences
import android.util.Log
import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Preferences.MAX_RECENT_SEARCHES
import com.automattic.simplenote.models.Preferences.PREFERENCES_OBJECT_KEY
import com.automattic.simplenote.search.SortOrder
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.StrUtils
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import com.simperium.client.BucketObjectNameInvalid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class SimperiumPreferencesRepository @Inject constructor(
    private val preferencesBucket: Bucket<Preferences>,
    private val sharedPreferences: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PreferencesRepository {

    // The repository is unscoped, so a per-instance bucket listener would leak on every injection.
    // Instead of listening, the snapshot is read from the bucket once at construction (matching the
    // one legacy read each static analyticsIsEnabled() call performed) and refreshed by every
    // suspend read and write. The construction-time read is guarded so dependency injection never
    // fails: any failure falls back to the legacy pre-init default of enabled analytics.
    private val analyticsSnapshot = AtomicBoolean(
        try {
            readAnalyticsEnabled()
        } catch (exception: RuntimeException) {
            Log.w(Simplenote.TAG, "Unable to read the analytics preference; defaulting to enabled", exception)
            DEFAULT_ANALYTICS_ENABLED
        }
    )

    override suspend fun isAnalyticsEnabled(): Boolean = withContext(ioDispatcher) {
        readAnalyticsEnabled().also(analyticsSnapshot::set)
    }

    override suspend fun setAnalyticsEnabled(enabled: Boolean) = withContext(ioDispatcher) {
        val preferences = getOrCreatePreferences() ?: return@withContext
        preferences.analyticsEnabled = enabled
        preferences.save()
        analyticsSnapshot.set(enabled)
    }

    override fun analyticsEnabledSnapshot(): Boolean = analyticsSnapshot.get()

    override suspend fun recentSearches(): List<String> = withContext(ioDispatcher) {
        getOrCreatePreferences()?.nonBlankRecentSearches() ?: emptyList()
    }

    override suspend fun addRecentSearch(query: String, index: Int) = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext
        val preferences = getOrCreatePreferences() ?: return@withContext
        val recents = preferences.nonBlankRecentSearches()
        recents.remove(query)
        recents.add(index.coerceIn(0, recents.size), query)
        preferences.setRecentSearches(recents.take(MAX_RECENT_SEARCHES))
        preferences.save()
    }

    override suspend fun removeRecentSearch(query: String): Int = withContext(ioDispatcher) {
        val preferences = getOrCreatePreferences() ?: return@withContext -1
        val recents = preferences.nonBlankRecentSearches()
        val removedIndex = recents.indexOf(query)
        recents.remove(query)
        preferences.setRecentSearches(recents)
        preferences.save()
        removedIndex
    }

    override fun preferencesChanged(): Flow<Unit> = callbackFlow {
        val onSaveListener = Bucket.OnSaveObjectListener<Preferences> { _, _ -> trySend(Unit) }
        val onDeleteListener = Bucket.OnDeleteObjectListener<Preferences> { _, _ -> trySend(Unit) }
        val onNetworkChangeListener = Bucket.OnNetworkChangeListener<Preferences> { _, _, _ -> trySend(Unit) }
        preferencesBucket.addOnSaveObjectListener(onSaveListener)
        preferencesBucket.addOnDeleteObjectListener(onDeleteListener)
        preferencesBucket.addOnNetworkChangeListener(onNetworkChangeListener)
        awaitClose {
            preferencesBucket.removeOnSaveObjectListener(onSaveListener)
            preferencesBucket.removeOnDeleteObjectListener(onDeleteListener)
            preferencesBucket.removeOnNetworkChangeListener(onNetworkChangeListener)
        }
    }.conflate().flowOn(ioDispatcher)

    override suspend fun sortOrder(): SortOrder = withContext(ioDispatcher) {
        val stored = try {
            sharedPreferences.getString(PrefUtils.PREF_SORT_ORDER, DEFAULT_SORT_ORDER)
        } catch (exception: ClassCastException) {
            DEFAULT_SORT_ORDER
        }
        SortOrder.fromPreference(StrUtils.strToInt(stored, 0))
    }

    private fun readAnalyticsEnabled(): Boolean = try {
        preferencesBucket.get(PREFERENCES_OBJECT_KEY).analyticsEnabled
    } catch (exception: BucketObjectMissingException) {
        DEFAULT_ANALYTICS_ENABLED
    }

    private fun Preferences.nonBlankRecentSearches() =
        recentSearches.filterNotTo(mutableListOf()) { it.isBlank() }

    private fun getOrCreatePreferences(): Preferences? = try {
        preferencesBucket.get(PREFERENCES_OBJECT_KEY)
    } catch (exception: BucketObjectMissingException) {
        try {
            preferencesBucket.newObject(PREFERENCES_OBJECT_KEY).also(Preferences::save)
        } catch (invalid: BucketObjectNameInvalid) {
            Log.e(Simplenote.TAG, "Could not create preferences entity", invalid)
            null
        }
    }

    companion object {
        private const val DEFAULT_ANALYTICS_ENABLED = true
        private const val DEFAULT_SORT_ORDER = "0"
    }
}
