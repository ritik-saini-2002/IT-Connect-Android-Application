package com.saini.ritik.di

import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.chat.ChatRepository
import com.saini.ritik.core.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        syncManager: SyncManager,
        authRepo   : AuthRepository
    ): ChatRepository = ChatRepository(syncManager, authRepo)
}