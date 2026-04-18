package com.example.ritik_2.localdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        RoleEntity::class,
        DepartmentEntity::class,
        CompanyEntity::class,
        CollectionEntity::class,
        SyncQueueEntity::class
    ],
    version  = 2,
    exportSchema = true
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

        /** Migration v1 -> v2: establishes migration infrastructure (no schema changes). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes — version bump to establish migration path
            }
        }

        fun getInstance(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "itconnect.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Cache-only DB: every entity is regenerable from PocketBase.
                    // If a future schema bump ships without a matching migration we'd
                    // rather wipe-and-rebuild than crash on first launch after upgrade.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}