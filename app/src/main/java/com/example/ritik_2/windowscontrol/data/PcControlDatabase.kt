package com.example.ritik_2.windowscontrol.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────
//  DAO — Plans
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
//  CONNECTION LOG ENTITY — stores PC connection history
//  NEW: Saves user profile info for each connection session
// ─────────────────────────────────────────────────────────────

@Entity(tableName = "pc_connection_logs")
data class PcConnectionLog(
    @PrimaryKey(autoGenerate = true)
    val id          : Long   = 0,
    val pcIp        : String = "",
    val pcPort      : Int    = 5000,
    val pcName      : String = "",
    val userName    : String = "",
    val userEmail   : String = "",
    val userRole    : String = "",
    val userCompany : String = "",
    val connectedAt : Long   = System.currentTimeMillis(),
    val disconnectedAt: Long = 0,
    val sessionDurationMs: Long = 0,
    val secretKeyHash: String = "",  // SHA256 first 8 chars — never store full key
    val agentVersion: String = ""
)

// ─────────────────────────────────────────────────────────────
//  DAO — Connection Logs
// ─────────────────────────────────────────────────────────────

@Dao
interface PcConnectionLogDao {
    @Query("SELECT * FROM pc_connection_logs ORDER BY connectedAt DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<PcConnectionLog>>

    @Query("SELECT * FROM pc_connection_logs ORDER BY connectedAt DESC LIMIT :limit")
    suspend fun getRecentLogsSync(limit: Int = 50): List<PcConnectionLog>

    @Insert
    suspend fun insert(log: PcConnectionLog): Long

    @Update
    suspend fun update(log: PcConnectionLog)

    @Query("UPDATE pc_connection_logs SET disconnectedAt = :time, sessionDurationMs = :duration WHERE id = :logId")
    suspend fun endSession(logId: Long, time: Long, duration: Long)

    @Query("DELETE FROM pc_connection_logs WHERE connectedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM pc_connection_logs")
    suspend fun count(): Int
}

// ─────────────────────────────────────────────────────────────
//  DATABASE — version 3 (added connection logs table)
// ─────────────────────────────────────────────────────────────

@Database(
    entities = [PcPlan::class, PcConnectionLog::class],
    version = 3,
    exportSchema = false
)
abstract class PcControlDatabase : RoomDatabase() {
    abstract fun planDao(): PcPlanDao
    abstract fun connectionLogDao(): PcConnectionLogDao

    companion object {
        @Volatile private var INSTANCE: PcControlDatabase? = null

        fun getDatabase(context: Context): PcControlDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PcControlDatabase::class.java,
                    "pccontrol_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  REPOSITORY — extended with connection logging
// ─────────────────────────────────────────────────────────────

class PcControlRepository(
    private val planDao: PcPlanDao,
    private val logDao: PcConnectionLogDao? = null
) {
    val allPlans: Flow<List<PcPlan>> = planDao.getAllPlans()

    suspend fun insertPlan(plan: PcPlan) = planDao.insert(plan)
    suspend fun deletePlan(plan: PcPlan) = planDao.delete(plan)
    suspend fun updatePlan(plan: PcPlan) = planDao.update(plan)
    suspend fun getPlanById(id: String)  = planDao.getById(id)

    suspend fun seedIfEmpty() {
        if (planDao.count() > 0) return
        val samples = listOf(
            PcPlan.create(planId = "sys_lock", planName = "Lock PC", icon = "🔒",
                steps = listOf(PcStep("SYSTEM_CMD", "LOCK"))),
            PcPlan.create(planId = "sys_sleep", planName = "Sleep PC", icon = "😴",
                steps = listOf(PcStep("SYSTEM_CMD", "SLEEP"))),
            PcPlan.create(planId = "sys_shutdown", planName = "Shutdown", icon = "⏻",
                steps = listOf(PcStep("SYSTEM_CMD", "SHUTDOWN"))),
            PcPlan.create(planId = "sys_restart", planName = "Restart", icon = "🔄",
                steps = listOf(PcStep("SYSTEM_CMD", "RESTART"))),
            PcPlan.create(planId = "sys_wake", planName = "Wake Screen", icon = "☀️",
                steps = listOf(
                    PcStep("SYSTEM_CMD", "WAKE_SCREEN"),
                    PcStep("WAIT", ms = 1000),
                    PcStep("KEY_PRESS", "ENTER")
                )),
            PcPlan.create(planId = "sys_unlock", planName = "Wake + Enter Password", icon = "🔑",
                steps = listOf(
                    PcStep("SYSTEM_CMD", "WAKE_SCREEN"),
                    PcStep("WAIT", ms = 1500),
                    PcStep("MOUSE_CLICK", x = 0, y = 0),
                    PcStep("WAIT", ms = 300),
                    PcStep("TYPE_TEXT", value = ""),
                    PcStep("KEY_PRESS", "ENTER")
                )),
            PcPlan.create(planId = "media_movie", planName = "Movie Night (VLC)", icon = "🎬",
                steps = listOf(
                    PcStep("LAUNCH_APP", "vlc.exe"),
                    PcStep("WAIT", ms = 2000),
                    PcStep("KEY_PRESS", "F11")
                )),
            PcPlan.create(planId = "media_ppt", planName = "Start Presentation", icon = "📊",
                steps = listOf(
                    PcStep("LAUNCH_APP", "powerpnt.exe"),
                    PcStep("WAIT", ms = 4000),
                    PcStep("KEY_PRESS", "F5")
                )),
            PcPlan.create(planId = "prod_screenshot", planName = "Screenshot", icon = "📸",
                steps = listOf(PcStep("SYSTEM_CMD", "SCREENSHOT"))),
            PcPlan.create(planId = "prod_mute", planName = "Toggle Mute", icon = "🔇",
                steps = listOf(PcStep("SYSTEM_CMD", "MUTE"))),
            PcPlan.create(planId = "prod_desktop", planName = "Show Desktop", icon = "🖥️",
                steps = listOf(PcStep("KEY_PRESS", "WIN+D")))
        )
        samples.forEach { planDao.insert(it) }
    }

    // ── Connection logging ────────────────────────────────────

    val connectionLogs: Flow<List<PcConnectionLog>>?
        get() = logDao?.getRecentLogs()

    suspend fun logConnection(log: PcConnectionLog): Long =
        logDao?.insert(log) ?: -1

    suspend fun endConnectionSession(logId: Long) {
        if (logId <= 0) return
        val now = System.currentTimeMillis()
        val logs = logDao?.getRecentLogsSync(1) ?: return
        val session = logs.find { it.id == logId } ?: return
        val duration = now - session.connectedAt
        logDao.endSession(logId, now, duration)
    }

    suspend fun getRecentConnectionLogs(): List<PcConnectionLog> =
        logDao?.getRecentLogsSync(50) ?: emptyList()

    suspend fun cleanOldLogs(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 86_400_000L)
        logDao?.deleteOlderThan(cutoff)
    }
}