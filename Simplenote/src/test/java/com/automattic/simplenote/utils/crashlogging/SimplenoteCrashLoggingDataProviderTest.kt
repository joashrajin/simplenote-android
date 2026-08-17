package com.automattic.simplenote.utils.crashlogging

import android.content.Context
import android.content.SharedPreferences
import com.automattic.android.tracks.crashlogging.CrashLoggingUser
import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.utils.locale.LocaleProvider
import com.simperium.android.AndroidClient
import com.simperium.android.AsyncAuthClient.USER_ACCESS_TOKEN_PREFERENCE
import com.simperium.android.AsyncAuthClient.USER_EMAIL_PREFERENCE
import com.simperium.client.Bucket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun userRestoresThePersistedAuthenticatedSession() = runTest {
        val app = mock<Simplenote>()
        val sharedPreferences = mock<SharedPreferences>()
        val email = "person@example.com"
        val provider = userProvider(app, sharedPreferences, { "token" }, { email })

        assertEquals(CrashLoggingUser(email = email), provider.user.first())

        verify(sharedPreferences).registerOnSharedPreferenceChangeListener(any())
        verify(sharedPreferences).unregisterOnSharedPreferenceChangeListener(any())
    }

    @Test
    fun userTracksLoginAndLogoutPreferenceChanges() = runTest {
        val app = mock<Simplenote>()
        val sharedPreferences = mock<SharedPreferences>()
        var accessToken: String? = null
        var email: String? = null
        val provider = userProvider(app, sharedPreferences, { accessToken }, { email })
        val users = mutableListOf<CrashLoggingUser?>()
        val collection = launch {
            provider.user.take(3).toList(users)
        }
        runCurrent()

        val listener = argumentCaptor<SharedPreferences.OnSharedPreferenceChangeListener>().run {
            verify(sharedPreferences).registerOnSharedPreferenceChangeListener(capture())
            firstValue
        }
        assertEquals(listOf<CrashLoggingUser?>(null), users)

        accessToken = "token"
        email = "person@example.com"
        listener.onSharedPreferenceChanged(sharedPreferences, USER_ACCESS_TOKEN_PREFERENCE)
        listener.onSharedPreferenceChanged(sharedPreferences, USER_EMAIL_PREFERENCE)
        runCurrent()
        assertEquals(listOf(null, CrashLoggingUser(email = "person@example.com")), users)

        accessToken = null
        email = null
        listener.onSharedPreferenceChanged(sharedPreferences, null)
        runCurrent()
        collection.join()

        assertEquals(
            listOf(null, CrashLoggingUser(email = "person@example.com"), null),
            users
        )
        verify(sharedPreferences).unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test
    fun userDoesNotRestoreAnOlderSnapshotAfterConcurrentLogin() = runTest {
        val app = mock<Simplenote>()
        val sharedPreferences = mock<SharedPreferences>()
        var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
        var loggedIn = false
        whenever(
            app.getSharedPreferences(AndroidClient.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).thenReturn(sharedPreferences)
        whenever(sharedPreferences.registerOnSharedPreferenceChangeListener(any())).thenAnswer {
            listener = it.getArgument(0)
            null
        }
        whenever(sharedPreferences.all).thenAnswer {
            if (!loggedIn) {
                loggedIn = true
                listener?.onSharedPreferenceChanged(sharedPreferences, USER_ACCESS_TOKEN_PREFERENCE)
            }
            credentialSnapshot("token", "person@example.com")
        }
        whenever(sharedPreferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null)).thenAnswer {
            if (!loggedIn) {
                loggedIn = true
                listener?.onSharedPreferenceChanged(sharedPreferences, USER_ACCESS_TOKEN_PREFERENCE)
                null
            } else {
                "token"
            }
        }
        whenever(sharedPreferences.getString(USER_EMAIL_PREFERENCE, null)).thenAnswer {
            if (loggedIn) "person@example.com" else null
        }
        val provider = SimplenoteCrashLoggingDataProvider(
            app,
            localeProvider,
            Provider { mock<PreferencesRepository>() }
        )
        val users = mutableListOf<CrashLoggingUser?>()
        val collection = launch {
            provider.user.toList(users)
        }

        runCurrent()

        assertEquals(listOf(CrashLoggingUser(email = "person@example.com")), users)
        collection.cancelAndJoin()
        verify(sharedPreferences).unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test
    fun userStaysAnonymousWithoutCompleteCredentials() = runTest {
        val app = mock<Simplenote>()
        val sharedPreferences = mock<SharedPreferences>()
        var accessToken = " "
        var email = "person@example.com"
        val provider = userProvider(app, sharedPreferences, { accessToken }, { email })

        assertNull(provider.user.first())

        accessToken = "token"
        email = "\t"
        assertNull(provider.user.first())
    }

    private fun userProvider(
        app: Simplenote,
        sharedPreferences: SharedPreferences,
        accessToken: () -> String?,
        email: () -> String?,
    ): SimplenoteCrashLoggingDataProvider {
        whenever(
            app.getSharedPreferences(AndroidClient.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).thenReturn(sharedPreferences)
        whenever(sharedPreferences.all).thenAnswer { credentialSnapshot(accessToken(), email()) }
        return SimplenoteCrashLoggingDataProvider(
            app,
            localeProvider,
            Provider { mock<PreferencesRepository>() }
        )
    }

    private fun credentialSnapshot(accessToken: String?, email: String?): Map<String, String> =
        buildMap {
            accessToken?.let { put(USER_ACCESS_TOKEN_PREFERENCE, it) }
            email?.let { put(USER_EMAIL_PREFERENCE, it) }
        }
}
