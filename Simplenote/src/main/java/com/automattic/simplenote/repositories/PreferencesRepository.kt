package com.automattic.simplenote.repositories

import com.automattic.simplenote.search.SortOrder
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    /** Defaults to true while the Simperium preferences object does not exist yet. */
    suspend fun isAnalyticsEnabled(): Boolean

    suspend fun setAnalyticsEnabled(enabled: Boolean)

    /**
     * Lock-free cached read for synchronous callers such as the static [com.automattic.simplenote.Simplenote.analyticsIsEnabled]
     * bridge. Initialized from the bucket at construction and refreshed by [isAnalyticsEnabled] and [setAnalyticsEnabled].
     */
    fun analyticsEnabledSnapshot(): Boolean

    suspend fun recentSearches(): List<String>

    suspend fun addRecentSearch(query: String, index: Int = 0)

    /**
     * Drops [query] from the recent searches and returns the index it occupied (-1 when it was
     * not stored) — the index the legacy NoteListFragment.deleteSearchItem captured for the
     * undo bar to restore through [addRecentSearch].
     */
    suspend fun removeRecentSearch(query: String): Int

    fun preferencesChanged(): Flow<Unit>

    suspend fun sortOrder(): SortOrder
}
