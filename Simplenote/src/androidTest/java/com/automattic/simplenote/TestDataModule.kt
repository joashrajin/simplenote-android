package com.automattic.simplenote

import android.content.Context
import com.automattic.simplenote.authentication.magiclink.OkHttpMagicLinkRepository
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
import com.automattic.simplenote.repositories.SimperiumAccountRepository
import com.automattic.simplenote.repositories.SimperiumCollaboratorsRepository
import com.automattic.simplenote.repositories.SimperiumNotesRepository
import com.automattic.simplenote.repositories.SimperiumPreferencesRepository
import com.automattic.simplenote.repositories.SimperiumTagsRepository
import com.automattic.simplenote.repositories.TagsRepository
import com.automattic.simplenote.utils.TestBucket
import com.simperium.Simperium
import com.simperium.client.Bucket
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.json.JSONObject
import javax.inject.Singleton

/**
 * Replaces [DataModule] for every @HiltAndroidTest in the instrumentation APK. The buckets are
 * in-memory [TestBucket] singletons — [BaseUITest] clears them between tests — and the repository
 * bindings stay on the production implementations, so integration tests exercise real repository
 * logic against the test buckets. Hilt builds a fresh component (and thus fresh buckets) for each
 * test instance.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {
    @Provides
    @Singleton
    fun providesNotesBucket(): Bucket<Note> = object : TestBucket<Note>("notes") {
        override fun build(key: String?): Note = Note(key)
    }

    @Provides
    @Singleton
    fun providesTagsBucket(): Bucket<Tag> = object : TestBucket<Tag>("tags") {
        override fun build(key: String?): Tag = Tag(key)
    }

    @Provides
    @Singleton
    fun providesPreferencesBucket(): Bucket<Preferences> = object : TestBucket<Preferences>("preferences") {
        override fun build(key: String?): Preferences = Preferences.Schema().build(key, JSONObject())
    }

    @Provides
    @Singleton
    fun providesAccountBucket(): Bucket<Account> = object : TestBucket<Account>("account") {
        override fun build(key: String?): Account = Account.Schema().build(key, JSONObject())
    }

    @Provides
    @Singleton
    fun providesNoteSyncTimes(): SyncTimes<Note> = SyncTimes(emptyMap())

    // Constructed only if a test's object graph actually requests it. Creating the client opens no
    // network connection (nothing starts the real buckets in tests), matching the idle client the
    // old SimplenoteTest application carried.
    @Provides
    @Singleton
    fun providesSimperium(@ApplicationContext context: Context): Simperium =
        Simperium.newClient(BuildConfig.SIMPERIUM_APP_ID, BuildConfig.SIMPERIUM_APP_KEY, context)

    @Provides
    @Singleton
    fun providesSimperiumBuckets(@ApplicationContext context: Context, simperium: Simperium): SimperiumBuckets =
        SimperiumBuckets(context, simperium)

    @Provides
    @Singleton
    fun providesTagsRepository(repository: SimperiumTagsRepository): TagsRepository = repository

    @Provides
    @Singleton
    fun providesCollaboratorsRepository(repository: SimperiumCollaboratorsRepository): CollaboratorsRepository =
        repository

    @Provides
    @Singleton
    fun providesMagicLinkRepository(repository: OkHttpMagicLinkRepository): MagicLinkRepository = repository

    @Provides
    @Singleton
    fun providesNotesRepository(repository: SimperiumNotesRepository): NotesRepository = repository

    // Unscoped, mirroring DataModule: the analytics snapshot freshness relies on per-injection
    // construction.
    @Provides
    fun providesPreferencesRepository(repository: SimperiumPreferencesRepository): PreferencesRepository = repository

    @Provides
    @Singleton
    fun providesAccountRepository(repository: SimperiumAccountRepository): AccountRepository = repository
}
