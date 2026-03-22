package com.example.ritik_2.windowscontrol.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────
//  DAO
// ─────────────────────────────────────────────────────────────

@Dao
interface PcPlanDao {
    @Query("SELECT * FROM pc_plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<PcPlan>>

    @Query("SELECT * FROM pc_plans WHERE planId = :planId")
    suspend fun getById(planId: String): PcPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: PcPlan)

    @Delete
    suspend fun delete(plan: PcPlan)

    @Update
    suspend fun update(plan: PcPlan)

    @Query("SELECT COUNT(*) FROM pc_plans")
    suspend fun count(): Int
}

// ─────────────────────────────────────────────────────────────
//  DATABASE — No @TypeConverters needed (stepsJson is plain String)
// ─────────────────────────────────────────────────────────────

@Database(entities = [PcPlan::class], version = 2, exportSchema = false)
// ✅ No @TypeConverters annotation — PcPlan.stepsJson is a plain String column
abstract class PcControlDatabase : RoomDatabase() {
    abstract fun planDao(): PcPlanDao

    companion object {
        @Volatile private var INSTANCE: PcControlDatabase? = null

        fun getDatabase(context: Context): PcControlDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PcControlDatabase::class.java,
                    "pccontrol_database"
                )
                    .fallbackToDestructiveMigration() // version 1→2: old steps column gone
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  REPOSITORY
// ─────────────────────────────────────────────────────────────

class PcControlRepository(private val dao: PcPlanDao) {

    val allPlans: Flow<List<PcPlan>> = dao.getAllPlans()

    suspend fun insertPlan(plan: PcPlan) = dao.insert(plan)
    suspend fun deletePlan(plan: PcPlan) = dao.delete(plan)
    suspend fun updatePlan(plan: PcPlan) = dao.update(plan)
    suspend fun getPlanById(id: String)  = dao.getById(id)

    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        // ✅ Use PcPlan.create() — not the constructor with steps = listOf(...)
        val samples = listOf(
            PcPlan.create(
                planId   = "pc_sample_01",
                planName = "Play Movie",
                icon     = "🎬",
                steps    = listOf(
                    PcStep("LAUNCH_APP", "vlc.exe", listOf("D:/Movies/movie.mp4")),
                    PcStep("WAIT", ms = 3000),
                    PcStep("KEY_PRESS", "F11")
                )
            ),
            PcPlan.create(
                planId   = "pc_sample_02",
                planName = "Start Presentation",
                icon     = "📊",
                steps    = listOf(
                    PcStep("LAUNCH_APP", "powerpnt.exe", listOf("D:/slides.pptx")),
                    PcStep("WAIT", ms = 4000),
                    PcStep("KEY_PRESS", "F5")
                )
            ),
            PcPlan.create(
                planId   = "pc_sample_03",
                planName = "Lock PC",
                icon     = "🔒",
                steps    = listOf(PcStep("SYSTEM_CMD", "LOCK"))
            )
        )
        samples.forEach { dao.insert(it) }
    }
}