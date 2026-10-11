package com.findmissingpep.app.di

import com.findmissingpep.app.data.repository.AuthRepositoryImpl
import com.findmissingpep.app.data.repository.CaseRepositoryImpl
import com.findmissingpep.app.data.repository.NotificationRepositoryImpl
import com.findmissingpep.app.data.repository.SightingRepositoryImpl
import com.findmissingpep.app.domain.repository.AuthRepository
import com.findmissingpep.app.domain.repository.CaseRepository
import com.findmissingpep.app.domain.repository.NotificationRepository
import com.findmissingpep.app.domain.repository.SightingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCaseRepository(impl: CaseRepositoryImpl): CaseRepository

    @Binds
    @Singleton
    abstract fun bindSightingRepository(impl: SightingRepositoryImpl): SightingRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}
