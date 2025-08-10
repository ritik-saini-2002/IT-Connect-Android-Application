package com.example.ritik_2.administrator.administratorpanel.databasemanager.module

import android.content.Context
import androidx.room.Room
import com.example.ritik_2.data.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "it_connect_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserDao(database: AppDatabase) = database.userDao()

    @Provides
    fun provideComplaintDao(database: AppDatabase) = database.complaintDao()

    @Provides
    fun provideCompanyDao(database: AppDatabase) = database.companyDao()

    @Provides
    fun provideDepartmentDao(database: AppDatabase) = database.departmentDao()
}