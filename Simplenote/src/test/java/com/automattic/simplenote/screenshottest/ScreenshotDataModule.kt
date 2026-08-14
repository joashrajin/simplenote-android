package com.automattic.simplenote.screenshottest

import com.automattic.simplenote.di.DataModule
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.repositories.CollaboratorsRepository
import com.automattic.simplenote.repositories.MagicLinkRepository
import com.automattic.simplenote.repositories.NotesRepository
import com.automattic.simplenote.repositories.PreferencesRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.simperium.Simperium
import com.simperium.client.Bucket
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.mockito.kotlin.mock

/**
 * Replaces [DataModule] for every @HiltAndroidTest in this compilation unit (the screenshot tests
 * are the only Hilt tests in the source set), so @AndroidEntryPoint activities can launch on top
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

    // Never exercised by the screenshot surfaces; provided only to keep the object graph of the
    // replaced module complete for bindings that inject Simperium types (e.g. SessionManager).
    @Provides
    fun providesTagsBucket(): Bucket<Tag> = mock()

    @Provides
    fun providesNotesBucket(): Bucket<Note> = mock()

    @Provides
    fun providesSimperium(): Simperium = mock()
}
