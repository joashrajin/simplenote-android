package com.automattic.simplenote.utils.crashlogging

import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.utils.locale.LocaleProvider
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
    fun analyticsDefaultsToEnabledBeforeTheBucketsExist() {
        val app = mock<Simplenote>()
        whenever(app.bucketsAreInitialized()).thenReturn(false)
        val repository = Provider<PreferencesRepository> {
            throw AssertionError("The repository must not be resolved before the buckets exist")
        }

        val provider = SimplenoteCrashLoggingDataProvider(app, localeProvider, repository)

        assertTrue(provider.analyticsEnabledForCrashLogging())
    }

    @Test
    fun analyticsReadsTheSnapshotOnceTheBucketsExist() {
        val app = mock<Simplenote>()
        whenever(app.bucketsAreInitialized()).thenReturn(true)
        val repository = mock<PreferencesRepository>()
        whenever(repository.analyticsEnabledSnapshot()).thenReturn(false)

        val provider = SimplenoteCrashLoggingDataProvider(app, localeProvider, Provider { repository })

        assertFalse(provider.analyticsEnabledForCrashLogging())
        verify(repository).analyticsEnabledSnapshot()
    }
}
