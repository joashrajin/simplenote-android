package com.automattic.simplenote.repositories

import android.content.SharedPreferences
import com.automattic.simplenote.CoroutineTestRule
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Preferences.PREFERENCES_OBJECT_KEY
import com.automattic.simplenote.search.SortOrder
import com.automattic.simplenote.utils.PrefUtils
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectMissingException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class SimperiumPreferencesRepositoryTest {
    @get:Rule
    val coroutinesTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    private lateinit var preferencesBucket: Bucket<Preferences>
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        preferencesBucket = mock()
        sharedPreferences = mock()
    }

    private fun repository() = SimperiumPreferencesRepository(
        preferencesBucket,
        sharedPreferences,
        coroutinesTestRule.testDispatcher,
    )

    private fun preferencesObject(): Preferences =
        Preferences.Schema().build(PREFERENCES_OBJECT_KEY, JSONObject()).also { preferences ->
            preferences.bucket = preferencesBucket
        }

    private fun storedPreferences(): Preferences = preferencesObject().also { preferences ->
        whenever(preferencesBucket.get(PREFERENCES_OBJECT_KEY)).thenReturn(preferences)
    }

    @Test
    fun analyticsDefaultsToEnabledWhenThePropertyIsMissing() = runTest {
        storedPreferences()
        val repository = repository()

        assertTrue(repository.isAnalyticsEnabled())
        assertTrue(repository.analyticsEnabledSnapshot())
    }

    @Test
    fun analyticsDefaultsToEnabledWhenThePreferencesObjectIsMissing() = runTest {
        whenever(preferencesBucket.get(PREFERENCES_OBJECT_KEY)).thenThrow(BucketObjectMissingException())
        val repository = repository()

        assertTrue(repository.isAnalyticsEnabled())
        assertTrue(repository.analyticsEnabledSnapshot())
    }

    @Test
    fun snapshotDefaultsToEnabledWhenTheBucketReadFails() {
        whenever(preferencesBucket.get(PREFERENCES_OBJECT_KEY)).thenThrow(RuntimeException("bucket unavailable"))

        assertTrue(repository().analyticsEnabledSnapshot())
    }

    @Test
    fun readsTheStoredAnalyticsFlag() = runTest {
        val preferences = storedPreferences()
        preferences.analyticsEnabled = false
        val repository = repository()

        assertFalse(repository.isAnalyticsEnabled())
        assertFalse(repository.analyticsEnabledSnapshot())
    }

    @Test
    fun snapshotInitializesFromTheBucketAtConstruction() {
        val preferences = storedPreferences()
        preferences.analyticsEnabled = false

        assertFalse(repository().analyticsEnabledSnapshot())
    }

    @Test
    fun readingAnalyticsRefreshesTheSnapshot() = runTest {
        val preferences = storedPreferences()
        val repository = repository()
        assertTrue(repository.analyticsEnabledSnapshot())

        preferences.analyticsEnabled = false

        assertFalse(repository.isAnalyticsEnabled())
        assertFalse(repository.analyticsEnabledSnapshot())
    }

    @Test
    fun setAnalyticsEnabledPersistsAndRefreshesTheSnapshot() = runTest {
        val preferences = storedPreferences()
        val repository = repository()
        assertTrue(repository.analyticsEnabledSnapshot())

        repository.setAnalyticsEnabled(false)

        assertFalse(preferences.analyticsEnabled)
        assertFalse(repository.analyticsEnabledSnapshot())
        verify(preferencesBucket).sync(preferences)
    }

    @Test
    fun recentSearchesReadsTheStoredList() = runTest {
        storedPreferences().setRecentSearches(listOf("alpha", "beta"))

        assertEquals(listOf("alpha", "beta"), repository().recentSearches())
    }

    @Test
    fun recentSearchesCreatesTheMissingPreferencesObject() = runTest {
        whenever(preferencesBucket.get(PREFERENCES_OBJECT_KEY)).thenThrow(BucketObjectMissingException())
        val created = preferencesObject()
        whenever(preferencesBucket.newObject(PREFERENCES_OBJECT_KEY)).thenReturn(created)

        assertEquals(emptyList<String>(), repository().recentSearches())
        verify(preferencesBucket).newObject(PREFERENCES_OBJECT_KEY)
        verify(preferencesBucket).sync(created)
    }

    @Test
    fun addRecentSearchPutsTheNewestFirst() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("beta", "gamma"))

        repository().addRecentSearch("alpha")

        assertEquals(listOf("alpha", "beta", "gamma"), preferences.recentSearches)
        verify(preferencesBucket).sync(preferences)
    }

    @Test
    fun addRecentSearchDeduplicatesAnExistingQuery() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("beta", "gamma"))

        repository().addRecentSearch("gamma")

        assertEquals(listOf("gamma", "beta"), preferences.recentSearches)
    }

    @Test
    fun addRecentSearchRestoresAtTheGivenIndexForUndo() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("alpha", "gamma"))

        repository().addRecentSearch("beta", 1)

        assertEquals(listOf("alpha", "beta", "gamma"), preferences.recentSearches)
    }

    @Test
    fun addRecentSearchClampsAnOutOfRangeIndex() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("alpha"))

        repository().addRecentSearch("beta", 9)

        assertEquals(listOf("alpha", "beta"), preferences.recentSearches)
    }

    @Test
    fun addRecentSearchTrimsToTheMaximum() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("one", "two", "three", "four", "five"))

        repository().addRecentSearch("zero")

        assertEquals(Preferences.MAX_RECENT_SEARCHES, preferences.recentSearches.size)
        assertEquals(listOf("zero", "one", "two", "three", "four"), preferences.recentSearches)
    }

    @Test
    fun removeRecentSearchDropsTheQuery() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("alpha", "beta"))

        repository().removeRecentSearch("alpha")

        assertEquals(listOf("beta"), preferences.recentSearches)
        verify(preferencesBucket).sync(preferences)
    }

    @Test
    fun removeRecentSearchReturnsTheIndexTheQueryOccupiedForUndo() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("alpha", "beta", "gamma"))

        assertEquals(1, repository().removeRecentSearch("beta"))
        assertEquals(listOf("alpha", "gamma"), preferences.recentSearches)
    }

    @Test
    fun removeRecentSearchReturnsMinusOneForAQueryThatWasNotStored() = runTest {
        val preferences = storedPreferences()
        preferences.setRecentSearches(listOf("alpha"))

        assertEquals(-1, repository().removeRecentSearch("zed"))
        // The legacy delete path saved unconditionally, even when nothing was removed.
        assertEquals(listOf("alpha"), preferences.recentSearches)
        verify(preferencesBucket).sync(preferences)
    }

    @Test
    fun sortOrderMapsTheStoredPreference() = runTest {
        whenever(sharedPreferences.getString(eq(PrefUtils.PREF_SORT_ORDER), any()))
            .thenReturn(PrefUtils.DATE_CREATED_DESCENDING.toString())

        assertEquals(SortOrder.CREATED_DESC, repository().sortOrder())
    }

    @Test
    fun sortOrderFallsBackToModifiedNewestFirst() = runTest {
        whenever(sharedPreferences.getString(eq(PrefUtils.PREF_SORT_ORDER), any()))
            .thenAnswer { invocation -> invocation.arguments[1] }

        assertEquals(SortOrder.MODIFIED_DESC, repository().sortOrder())
    }

    @Test
    fun sortOrderSurvivesANonStringStoredPreference() = runTest {
        whenever(sharedPreferences.getString(eq(PrefUtils.PREF_SORT_ORDER), any()))
            .thenThrow(ClassCastException())

        assertEquals(SortOrder.MODIFIED_DESC, repository().sortOrder())
    }

    @Test
    fun preferencesChangedEmitsOnBucketCallbacksAndUnregistersOnCancel() = runTest {
        val repository = repository()
        val emissions = mutableListOf<Unit>()
        val job = launch(coroutinesTestRule.testDispatcher) {
            repository.preferencesChanged().collect { emissions.add(it) }
        }
        advanceUntilIdle()

        val saveListener = argumentCaptor<Bucket.OnSaveObjectListener<Preferences>>().run {
            verify(preferencesBucket).addOnSaveObjectListener(capture())
            firstValue
        }
        val deleteListener = argumentCaptor<Bucket.OnDeleteObjectListener<Preferences>>().run {
            verify(preferencesBucket).addOnDeleteObjectListener(capture())
            firstValue
        }
        val networkListener = argumentCaptor<Bucket.OnNetworkChangeListener<Preferences>>().run {
            verify(preferencesBucket).addOnNetworkChangeListener(capture())
            firstValue
        }

        val preferences = preferencesObject()
        saveListener.onSaveObject(preferencesBucket, preferences)
        advanceUntilIdle()
        assertEquals(1, emissions.size)

        networkListener.onNetworkChange(preferencesBucket, Bucket.ChangeType.MODIFY, PREFERENCES_OBJECT_KEY)
        advanceUntilIdle()
        assertEquals(2, emissions.size)

        deleteListener.onDeleteObject(preferencesBucket, preferences)
        advanceUntilIdle()
        assertEquals(3, emissions.size)

        verify(preferencesBucket, never()).removeOnSaveObjectListener(any())
        job.cancel()
        advanceUntilIdle()

        verify(preferencesBucket).removeOnSaveObjectListener(saveListener)
        verify(preferencesBucket).removeOnDeleteObjectListener(deleteListener)
        verify(preferencesBucket).removeOnNetworkChangeListener(networkListener)
    }

    @Test
    fun setAnalyticsEnabledCreatesTheMissingPreferencesObject() = runTest {
        whenever(preferencesBucket.get(PREFERENCES_OBJECT_KEY)).thenThrow(BucketObjectMissingException())
        val created = preferencesObject()
        whenever(preferencesBucket.newObject(PREFERENCES_OBJECT_KEY)).thenReturn(created)
        val repository = repository()

        repository.setAnalyticsEnabled(false)

        assertFalse(created.analyticsEnabled)
        assertFalse(repository.analyticsEnabledSnapshot())
        verify(preferencesBucket, atLeastOnce()).sync(created)
    }
}
