package com.example.ritik_2.di

import android.content.Context
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── SessionManager ────────────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext ctx: Context
    ): SessionManager = SessionManager(ctx)

    // ── OkHttpClient ──────────────────────────────────────────────────────────
    // Required by PocketBaseDataSource, ManageUserViewModel,
    // RoleManagementViewModel, DatabaseManagerViewModel, etc.
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)   // longer for image uploads
            .build()

    // ── AuthRepository ────────────────────────────────────────────────────────
    // Provided here (not bound in DataModule) because it is a concrete class,
    // not an interface, so @Binds does not apply.
    @Provides
    @Singleton
    fun provideAuthRepository(
        dataSource    : AppDataSource,
        sessionManager: SessionManager
    ): AuthRepository = AuthRepository(dataSource, sessionManager)
}