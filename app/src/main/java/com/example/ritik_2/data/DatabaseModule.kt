//package com.example.ritik_2.data
//
//import android.content.Context
//import androidx.room.Room
//import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.CompanyDao
//import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.ComplaintDao
//import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.DepartmentDao
//import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.UserDao
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.InstallIn
//import dagger.hilt.android.qualifiers.ApplicationContext
//import dagger.hilt.components.SingletonComponent
//import javax.inject.Singleton
//
//@Module
//@InstallIn(SingletonComponent::class)
//object DatabaseModule {
//
//    @Provides
//    @Singleton
//    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
//        return AppDatabase.getDatabase(context)
//    }
//
//    @Provides
//    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()
//
//    @Provides
//    fun provideUserProfileDao(database: AppDatabase): UserProfileDao = database.userProfileDao()
//
//    @Provides
//    fun provideWorkStatsDao(database: AppDatabase): WorkStatsDao = database.workStatsDao()
//
//    @Provides
//    fun provideComplaintDao(database: AppDatabase): ComplaintDao = database.complaintDao()
//
//    @Provides
//    fun provideCompanyDao(database: AppDatabase): CompanyDao = database.companyDao()
//
//    @Provides
//    fun provideDepartmentDao(database: AppDatabase): DepartmentDao = database.departmentDao()
//}