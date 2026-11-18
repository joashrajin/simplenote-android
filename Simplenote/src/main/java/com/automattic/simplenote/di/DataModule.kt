package com.automattic.simplenote.di

import android.content.Context
import com.automattic.simplenote.BuildConfig
import com.automattic.simplenote.SimperiumBuckets
import com.automattic.simplenote.SyncTimes
import com.automattic.simplenote.authentication.magiclink.OkHttpMagicLinkRepository
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
import com.automattic.simplenote.utils.crashlogging.CrashLoggingInitializer
import com.simperium.Simperium
import com.simperium.client.Bucket
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    companion object {
        private const val SIMPERIUM_AUTH_PROVIDER = "simplenote.com"

        // The client construction and the static auth provider moved here from Simplenote.onCreate;
        // the heartbeat watchdog stays in Simplenote, which registers itself as the listener. The
        // initializer parameter is ordering-only: Sentry must be live before the client and the
        // buckets are constructed, as it was when onCreate initialized it before all bucket work.
        @Provides
        @Singleton
        fun providesSimperium(
            @ApplicationContext context: Context,
            crashLoggingInitializer: CrashLoggingInitializer,
        ): Simperium = Simperium.newClient(
            BuildConfig.SIMPERIUM_APP_ID,
            BuildConfig.SIMPERIUM_APP_KEY,
            context,
        ).apply {
            setAuthProvider(SIMPERIUM_AUTH_PROVIDER)
        }

        @Provides
        @Singleton
        fun providesSimperiumBuckets(@ApplicationContext context: Context, simperium: Simperium): SimperiumBuckets =
            SimperiumBuckets(context, simperium)

        @Provides
        @Singleton
        fun providesTagsBucket(buckets: SimperiumBuckets): Bucket<Tag> = buckets.tags

        @Provides
        @Singleton
        fun providesNotesBucket(buckets: SimperiumBuckets): Bucket<Note> = buckets.notes

        @Provides
        @Singleton
        fun providesPreferencesBucket(buckets: SimperiumBuckets): Bucket<Preferences> = buckets.preferences

        @Provides
        @Singleton
        fun providesAccountBucket(buckets: SimperiumBuckets): Bucket<Account> = buckets.account

        @Provides
        @Singleton
        fun providesNoteSyncTimes(buckets: SimperiumBuckets): SyncTimes<Note> = buckets.noteSyncTimes
    }

    // The buckets are constructed once in [SimperiumBuckets] and are never replaced for the life
    // of the process — logout resets and stops them in place (SimperiumAccountRepository.logout) —
    // so singleton repositories hold valid bucket references forever.
    @Binds
    @Singleton
    abstract fun bindsTagsRepository(repository: SimperiumTagsRepository): TagsRepository

    @Binds
    @Singleton
    abstract fun bindsCollaboratorsRepository(repository: SimperiumCollaboratorsRepository): CollaboratorsRepository

    @Binds
    @Singleton
    abstract fun bindsMagicLinkRepository(repository: OkHttpMagicLinkRepository): MagicLinkRepository

    @Binds
    @Singleton
    abstract fun bindsNotesRepository(repository: SimperiumNotesRepository): NotesRepository

    // Deliberately unscoped: the repository reads its analytics snapshot from the bucket at
    // construction, so per-injection construction is what keeps the synchronous analytics gate
    // (Simplenote.analyticsIsEnabled, SimplenoteCrashLoggingDataProvider) fresh. See the
    // analyticsSnapshot comment in SimperiumPreferencesRepository before scoping this.
    @Binds
    abstract fun bindsPreferencesRepository(repository: SimperiumPreferencesRepository): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindsAccountRepository(repository: SimperiumAccountRepository): AccountRepository
}
