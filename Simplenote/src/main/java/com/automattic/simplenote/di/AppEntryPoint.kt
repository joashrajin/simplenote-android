package com.automattic.simplenote.di

import com.automattic.simplenote.models.Account
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Preferences
import com.automattic.simplenote.models.Tag
import com.simperium.Simperium
import com.simperium.client.Bucket
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun simperium(): Simperium
    fun notesBucket(): Bucket<Note>
    fun tagsBucket(): Bucket<Tag>
    fun preferencesBucket(): Bucket<Preferences>
    fun accountBucket(): Bucket<Account>
}
