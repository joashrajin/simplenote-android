package com.automattic.simplenote.di

import com.automattic.simplenote.Simplenote
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
import com.simperium.Simperium
import com.simperium.client.Bucket
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    companion object {
        @Provides
        fun providesAccountBucket(simplenote: Simplenote): Bucket<Account> = simplenote.accountBucket

        @Provides
        fun providesTagsBucket(simplenote: Simplenote): Bucket<Tag> = simplenote.tagsBucket

        @Provides
        fun providesNotesBucket(simplenote: Simplenote): Bucket<Note> = simplenote.notesBucket

        @Provides
        fun providesPreferencesBucket(simplenote: Simplenote): Bucket<Preferences> = simplenote.preferencesBucket

        @Provides
        fun providesSimperium(simplenote: Simplenote): Simperium = simplenote.simperium
    }

    // Repository binds stay unscoped until bucket ownership moves into DI: a singleton
    // repository would freeze the SimplenoteTest.useTestBucket seam at first injection
    // for the whole instrumentation process.
    @Binds
    abstract fun bindsAccountRepository(repository: SimperiumAccountRepository): AccountRepository

    @Binds
    abstract fun bindsTagsRepository(repository: SimperiumTagsRepository): TagsRepository

    @Binds
    abstract fun bindsCollaboratorsRepository(repository: SimperiumCollaboratorsRepository): CollaboratorsRepository

    @Binds
    abstract fun bindsMagicLinkRepository(repository: OkHttpMagicLinkRepository): MagicLinkRepository

    @Binds
    abstract fun bindsNotesRepository(repository: SimperiumNotesRepository): NotesRepository

    @Binds
    abstract fun bindsPreferencesRepository(repository: SimperiumPreferencesRepository): PreferencesRepository
}
