package com.automattic.simplenote.screenshottest

import com.automattic.simplenote.SimperiumBuckets
import com.automattic.simplenote.SyncTimes
import com.automattic.simplenote.di.DataModule
import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.CollaboratorsRepository
import com.automattic.simplenote.repositories.MagicLinkRepository
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.simperium.Simperium
import com.simperium.client.Bucket
import com.simperium.client.User
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Replaces [DataModule] for every @HiltAndroidTest in this compilation unit (the screenshot tests
 * and the widget configure activity tests), so @AndroidEntryPoint activities can launch on top
 * of [dagger.hilt.android.testing.HiltTestApplication] without a Simperium session.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object ScreenshotDataModule {
    @Provides
    fun providesTagsRepository(): TagsRepository = ScreenshotTagsRepository()

    @Provides
    fun providesCollaboratorsRepository(): CollaboratorsRepository = ScreenshotCollaboratorsRepository()

    @Provides
    fun providesNotesRepository(): NotesRepository = ScreenshotNotesRepository()

    @Provides
    fun providesPreferencesRepository(): PreferencesRepository = ScreenshotPreferencesRepository()

    @Provides
    fun providesMagicLinkRepository(): MagicLinkRepository = ScreenshotMagicLinkRepository()

    @Provides
    fun providesAccountRepository(): AccountRepository = ScreenshotAccountRepository()

    // Never exercised by the screenshot surfaces; provided only to keep the object graph of the
    // replaced module complete for bindings that inject Simperium types (including the
    // Simplenote application's own members injector, which asks for Simperium and the holder).
    @Provides
    fun providesTagsBucket(): Bucket<Tag> = mock()

    @Provides
    fun providesNotesBucket(): Bucket<Note> = mock()

    @Provides
    fun providesPreferencesBucket(): Bucket<Preferences> = mock()

    @Provides
    fun providesAccountBucket(): Bucket<Account> = mock()

    // The widget configure activities read the session straight off Simperium; it is signed out,
    // matching ScreenshotAccountRepository.
    @Provides
    fun providesSimperium(): Simperium {
        val signedOutUser = mock<User> { on { status } doReturn User.Status.NOT_AUTHORIZED }
        return mock { on { user } doReturn signedOutUser }
    }

    @Provides
    fun providesSimperiumBuckets(): SimperiumBuckets = mock()

    // InfoBottomSheetDialog injects the sync-time lookup; with no Simperium session every
    // screenshot note reads as never synced.
    @Provides
    fun providesNoteSyncTimes(): SyncTimes<Note> = SyncTimes(emptyMap())
}
