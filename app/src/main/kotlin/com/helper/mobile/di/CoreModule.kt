package com.helper.mobile.di

import com.helper.mobile.domain.repository.IdGenerator
import com.helper.mobile.domain.repository.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun timeSource(): TimeSource = object : TimeSource {
        override fun now(): Instant = Instant.now()
    }

    @Provides
    @Singleton
    fun idGenerator(): IdGenerator = object : IdGenerator {
        override fun newId(): String = UUID.randomUUID().toString()
    }
}
