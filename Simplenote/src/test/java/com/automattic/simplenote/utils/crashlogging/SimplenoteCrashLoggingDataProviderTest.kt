package com.automattic.simplenote.utils.crashlogging

import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.utils.locale.LocaleProvider
import com.simperium.client.Bucket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

class SimplenoteCrashLoggingDataProviderTest {
    private val localeProvider = mock<LocaleProvider>()

    @Test
    fun analyticsDefaultsToEnabledBeforeTheBucketExists() {
        val app = mock<Simplenote>()
        whenever(app.preferencesBucket).thenReturn(null)
        val repository = Provider<PreferencesRepository> {
            throw AssertionError("The repository must not be resolved before the bucket exists")
        }

        val provider = SimplenoteCrashLoggingDataProvider(app, localeProvider, repository)

        assertTrue(provider.analyticsEnabledForCrashLogging())
    }

    @Test
    fun analyticsReadsTheSnapshotOnceTheBucketExists() {
        val app = mock<Simplenote>()
        whenever(app.preferencesBucket).thenReturn(mock<Bucket<Preferences>>())
        val repository = mock<PreferencesRepository>()
        whenever(repository.analyticsEnabledSnapshot()).thenReturn(false)

        val provider = SimplenoteCrashLoggingDataProvider(app, localeProvider, Provider { repository })

        assertFalse(provider.analyticsEnabledForCrashLogging())
        verify(repository).analyticsEnabledSnapshot()
    }
}
