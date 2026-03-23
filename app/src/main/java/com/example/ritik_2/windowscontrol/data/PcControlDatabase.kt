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
        val samples = listOf(

            // ── Default System Plans ────────────────────────

            PcPlan.create(
                planId   = "sys_lock",
                planName = "Lock PC",
                icon     = "🔒",
                steps    = listOf(PcStep("SYSTEM_CMD", "LOCK"))
            ),
            PcPlan.create(
                planId   = "sys_sleep",
                planName = "Sleep PC",
                icon     = "😴",
                steps    = listOf(PcStep("SYSTEM_CMD", "SLEEP"))
            ),
            PcPlan.create(
                planId   = "sys_shutdown",
                planName = "Shutdown",
                icon     = "⏻",
                steps    = listOf(PcStep("SYSTEM_CMD", "SHUTDOWN"))
            ),
            PcPlan.create(
                planId   = "sys_restart",
                planName = "Restart",
                icon     = "🔄",
                steps    = listOf(PcStep("SYSTEM_CMD", "RESTART"))
            ),

            // ── Wake + Login ────────────────────────────────

            PcPlan.create(
                planId   = "sys_wake",
                planName = "Wake Screen",
                icon     = "☀️",
                steps    = listOf(
                    PcStep("SYSTEM_CMD", "WAKE_SCREEN"),  // wakes from sleep
                    PcStep("WAIT", ms = 1000),
                    PcStep("KEY_PRESS", "ENTER")           // dismiss lock screen
                )
            ),
            PcPlan.create(
                planId   = "sys_unlock",
                planName = "Wake + Enter Password",
                icon     = "🔑",
                steps    = listOf(
                    PcStep("SYSTEM_CMD", "WAKE_SCREEN"),
                    PcStep("WAIT", ms = 1500),
                    PcStep("MOUSE_CLICK", x = 0, y = 0),  // click center to focus
                    PcStep("WAIT", ms = 300),
                    PcStep("TYPE_TEXT", value = ""),       // ← user edits this: enter password
                    PcStep("KEY_PRESS", "ENTER")
                )
            ),

            // ── Media Plans ─────────────────────────────────

            PcPlan.create(
                planId   = "media_movie",
                planName = "Movie Night (VLC)",
                icon     = "🎬",
                steps    = listOf(
                    PcStep("LAUNCH_APP", "vlc.exe"),
                    PcStep("WAIT", ms = 2000),
                    PcStep("KEY_PRESS", "F11")             // fullscreen
                )
            ),
            PcPlan.create(
                planId   = "media_ppt",
                planName = "Start Presentation",
                icon     = "📊",
                steps    = listOf(
                    PcStep("LAUNCH_APP", "powerpnt.exe"),
                    PcStep("WAIT", ms = 4000),
                    PcStep("KEY_PRESS", "F5")              // slideshow
                )
            ),

            // ── Productivity ─────────────────────────────────

            PcPlan.create(
                planId   = "prod_screenshot",
                planName = "Screenshot",
                icon     = "📸",
                steps    = listOf(PcStep("SYSTEM_CMD", "SCREENSHOT"))
            ),
            PcPlan.create(
                planId   = "prod_mute",
                planName = "Toggle Mute",
                icon     = "🔇",
                steps    = listOf(PcStep("SYSTEM_CMD", "MUTE"))
            ),
            PcPlan.create(
                planId   = "prod_desktop",
                planName = "Show Desktop",
                icon     = "🖥️",
                steps    = listOf(PcStep("KEY_PRESS", "WIN+D"))
            )
        )
        samples.forEach { dao.insert(it) }
    }
}