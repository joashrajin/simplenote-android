package com.automattic.simplenote.di

import com.automattic.simplenote.networking.HeadersInterceptor
import com.automattic.simplenote.networking.SimpleHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val TIMEOUT_SECS = 30

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient().newBuilder()
        .addInterceptor(HeadersInterceptor())
        .readTimeout(TIMEOUT_SECS.toLong(), TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideSimpleHttp(okHttpClient: OkHttpClient): SimpleHttp = SimpleHttp(okHttpClient)
}
