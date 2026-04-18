package com.example.ritik_2.di

import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.chat.ChatRepository
import com.example.ritik_2.core.SyncManager
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