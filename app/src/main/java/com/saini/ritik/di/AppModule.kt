package com.saini.ritik.di

import android.content.Context
import com.saini.ritik.appupdate.AppUpdateChecker
import com.saini.ritik.appupdate.AppUpdateManager
import com.saini.ritik.auth.AuthRepository
import com.saini.ritik.core.AdminTokenProvider
import com.saini.ritik.core.ConnectivityMonitor
import com.saini.ritik.core.PrivateNetworkInterceptor
import com.saini.ritik.core.SyncManager
import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.localdatabase.AppDatabase
import com.saini.ritik.pocketbase.PocketBaseDataSource
import com.saini.ritik.pocketbase.SessionManager
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
            .addInterceptor(PrivateNetworkInterceptor())
            // Keep connect/read short so a single unreachable endpoint cannot
            // cumulatively burn 60s+ on the login path (sequential probes).
            .connectTimeout(130, TimeUnit.SECONDS)
            .readTimeout(130, TimeUnit.SECONDS)
            // Writes (uploads) keep a generous timeout for slow LANs.
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun provideConnectivityMonitor(@ApplicationContext ctx: Context): ConnectivityMonitor =
        ConnectivityMonitor(ctx)

    @Provides @Singleton
    fun provideAdminTokenProvider(@ApplicationContext ctx: Context): AdminTokenProvider =
        AdminTokenProvider(ctx)

    @Provides @Singleton
    fun provideSyncManager(
        db     : AppDatabase,
        http   : OkHttpClient,
        monitor: ConnectivityMonitor,
        adminTokenProvider: AdminTokenProvider
    ): SyncManager = SyncManager(db, http, monitor, adminTokenProvider)

    @Provides @Singleton
    fun providePocketBaseDataSource(
        http: OkHttpClient,
        db  : AppDatabase,
        adminTokenProvider: AdminTokenProvider
    ): PocketBaseDataSource = PocketBaseDataSource(http, db, adminTokenProvider)

    @Provides @Singleton
    fun provideAuthRepository(
        dataSource         : AppDataSource,
        pbDataSource       : PocketBaseDataSource,
        sessionManager     : SessionManager,
        syncManager        : SyncManager,
        adminTokenProvider : AdminTokenProvider
    ): AuthRepository = AuthRepository(dataSource, pbDataSource, sessionManager, syncManager, adminTokenProvider)

    @Provides @Singleton
   fun provideAppUpdateChecker(http: OkHttpClient): AppUpdateChecker =
        AppUpdateChecker(http)

   @Provides @Singleton
   fun provideAppUpdateManager(
       @ApplicationContext ctx: Context,
       http: OkHttpClient
   ): AppUpdateManager = AppUpdateManager(ctx, http)
}
