package com.example.ritik_2.di

import android.content.Context
import com.example.ritik_2.auth.AuthRepository
import com.example.ritik_2.core.ConnectivityMonitor
import com.example.ritik_2.core.SyncManager
import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.localdatabase.AppDatabase
import com.example.ritik_2.pocketbase.PocketBaseDataSource
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

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        AppDatabase.getInstance(ctx)

    @Provides @Singleton
    fun provideSessionManager(@ApplicationContext ctx: Context): SessionManager =
        SessionManager(ctx)

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun provideConnectivityMonitor(@ApplicationContext ctx: Context): ConnectivityMonitor =
        ConnectivityMonitor(ctx)

    @Provides @Singleton
    fun provideSyncManager(
        db     : AppDatabase,
        http   : OkHttpClient,
        monitor: ConnectivityMonitor
    ): SyncManager = SyncManager(db, http, monitor)

    // PocketBaseDataSource now receives AppDatabase for offline caching
    @Provides @Singleton
    fun providePocketBaseDataSource(
        http: OkHttpClient,
        db  : AppDatabase
    ): PocketBaseDataSource = PocketBaseDataSource(http, db)

    @Provides @Singleton
    fun provideAuthRepository(
        dataSource    : AppDataSource,
        pbDataSource  : PocketBaseDataSource,
        sessionManager: SessionManager,
        syncManager   : SyncManager
    ): AuthRepository = AuthRepository(dataSource, pbDataSource, sessionManager, syncManager)
}