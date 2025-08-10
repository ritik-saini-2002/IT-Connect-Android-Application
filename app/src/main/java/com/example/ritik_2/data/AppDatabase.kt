package com.example.ritik_2.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.CompanyDao
import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.ComplaintDao
import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.DepartmentDao
import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.UserDao
import com.example.ritik_2.data.*

@Database(
    entities = [
        User::class,
        UserProfile::class,
        WorkStats::class,
        Complaint::class,
        Company::class,
        Department::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverters::class, StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    // Abstract DAOs
    abstract fun userDao(): UserDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun workStatsDao(): WorkStatsDao
    abstract fun complaintDao(): ComplaintDao
    abstract fun companyDao(): CompanyDao
    abstract fun departmentDao(): DepartmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "it_connect_database"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addTypeConverter(DateConverters())
                    .addTypeConverter(StringListConverter())
                    .fallbackToDestructiveMigration() // Remove in production
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // For testing purposes
        fun getInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .addTypeConverter(DateConverters())
                .addTypeConverter(StringListConverter())
                .allowMainThreadQueries() // Only for testing
                .build()
        }

        // Migration example (for future versions)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add migration logic here when you need to update schema
                // Example: database.execSQL("ALTER TABLE users ADD COLUMN new_column TEXT")
            }
        }
    }
}