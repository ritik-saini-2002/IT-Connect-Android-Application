package com.example.ritik_2.localdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class,
        RoleEntity::class,
        DepartmentEntity::class,
        CompanyEntity::class,
        CollectionEntity::class,
        SyncQueueEntity::class
    ],
    version  = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao()      : UserDao
    abstract fun roleDao()      : RoleDao
    abstract fun deptDao()      : DepartmentDao
    abstract fun companyDao()   : CompanyDao
    abstract fun collectionDao(): CollectionDao
    abstract fun syncQueueDao() : SyncQueueDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "itconnect.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}