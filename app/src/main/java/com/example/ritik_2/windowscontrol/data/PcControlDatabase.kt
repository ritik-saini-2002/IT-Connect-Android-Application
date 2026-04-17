package com.example.ritik_2.windowscontrol.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
//  SAVED DEVICE ENTITY — stores user's known PCs for instant reconnect.
//  Secret key is stored in cleartext because SharedPreferences already
//  holds the active one — any device-level attacker reads both. If this
//  ever needs hardening, swap this column for an EncryptedSharedPreferences
//  lookup keyed by id.
// ─────────────────────────────────────────────────────────────

@Entity(tableName = "pc_saved_devices")
data class PcSavedDevice(
    @PrimaryKey val id        : String = java.util.UUID.randomUUID().toString(),
    val label     : String    = "",
    val host      : String    = "",       // IP or hostname
    val port      : Int       = 5000,
    val streamPort: Int       = 5001,
    val secretKey : String    = "",
    val isMaster  : Boolean   = false,    // stored key is master (admin) key
    val pcName    : String    = "",       // reported by agent during scan
    val addedAt   : Long      = System.currentTimeMillis(),
    val lastUsed  : Long      = 0L,
    val lastSeenOnline: Long  = 0L,
    // v6 additions — thumbnails + Wake-on-LAN
    val thumbnailPath     : String? = null,
    val thumbnailUpdatedAt: Long    = 0L,
    val macAddress        : String? = null,
    val broadcastAddress  : String? = null,   // null = auto-pick active iface broadcast
    val wolPort           : Int     = 9
)

@Dao
interface PcSavedDeviceDao {
    @Query("SELECT * FROM pc_saved_devices ORDER BY lastUsed DESC, addedAt DESC")
    fun getAll(): Flow<List<PcSavedDevice>>

    @Query("SELECT * FROM pc_saved_devices ORDER BY lastUsed DESC, addedAt DESC")
    suspend fun getAllSync(): List<PcSavedDevice>

    @Query("SELECT * FROM pc_saved_devices WHERE host = :host AND port = :port LIMIT 1")
    suspend fun findByHostPort(host: String, port: Int): PcSavedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: PcSavedDevice)

    @Delete
    suspend fun delete(device: PcSavedDevice)

    @Query("UPDATE pc_saved_devices SET lastUsed = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE pc_saved_devices SET lastSeenOnline = :ts, pcName = :pcName WHERE id = :id")
    suspend fun markOnline(id: String, ts: Long = System.currentTimeMillis(), pcName: String)

    @Query("UPDATE pc_saved_devices SET thumbnailPath = :path, thumbnailUpdatedAt = :ts WHERE id = :id")
    suspend fun setThumbnail(id: String, path: String?, ts: Long)

    @Query("SELECT * FROM pc_saved_devices WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PcSavedDevice?
}

// ─────────────────────────────────────────────────────────────
//  DATABASE — version 6 (thumbnail cache + Wake-on-LAN fields)
// ─────────────────────────────────────────────────────────────

@Database(
    entities = [PcPlan::class, PcConnectionLog::class, PcSavedDevice::class],
    version = 6,
    exportSchema = true
)
abstract class PcControlDatabase : RoomDatabase() {
    abstract fun planDao(): PcPlanDao
    abstract fun connectionLogDao(): PcConnectionLogDao
    abstract fun savedDeviceDao(): PcSavedDeviceDao

    companion object {
        @Volatile private var INSTANCE: PcControlDatabase? = null

        /** Migration v3 -> v4: establishes migration infrastructure (no schema changes). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes — version bump to establish migration path
            }
        }

        /** Migration v4 -> v5: saved devices table. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pc_saved_devices` (" +
                        "`id` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`host` TEXT NOT NULL, " +
                        "`port` INTEGER NOT NULL, " +
                        "`streamPort` INTEGER NOT NULL, " +
                        "`secretKey` TEXT NOT NULL, " +
                        "`isMaster` INTEGER NOT NULL, " +
                        "`pcName` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "`lastUsed` INTEGER NOT NULL, " +
                        "`lastSeenOnline` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /** Migration v5 -> v6: thumbnail cache + Wake-on-LAN fields on pc_saved_devices. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pc_saved_devices ADD COLUMN thumbnailPath TEXT")
                db.execSQL("ALTER TABLE pc_saved_devices ADD COLUMN thumbnailUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pc_saved_devices ADD COLUMN macAddress TEXT")
                db.execSQL("ALTER TABLE pc_saved_devices ADD COLUMN broadcastAddress TEXT")
                db.execSQL("ALTER TABLE pc_saved_devices ADD COLUMN wolPort INTEGER NOT NULL DEFAULT 9")
            }
        }

        fun getDatabase(context: Context): PcControlDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PcControlDatabase::class.java,
                    "pccontrol_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
    private val logDao: PcConnectionLogDao? = null,
    private val deviceDao: PcSavedDeviceDao? = null
) {
    val allPlans: Flow<List<PcPlan>> = planDao.getAllPlans()

    suspend fun insertPlan(plan: PcPlan) = planDao.insert(plan)
    suspend fun deletePlan(plan: PcPlan) = planDao.delete(plan)
    suspend fun updatePlan(plan: PcPlan) = planDao.update(plan)
    suspend fun getPlanById(id: String)  = planDao.getById(id)

    suspend fun seedIfEmpty() {
        if (planDao.count() > 0) return
        val samples = listOf(
            PcPlan.create(planId = "sys_lock", planName = "Lock PC",
                steps = listOf(PcStep("SYSTEM_CMD", "LOCK"))),
            PcPlan.create(planId = "sys_sleep", planName = "Sleep PC",
                steps = listOf(PcStep("SYSTEM_CMD", "SLEEP"))),
            PcPlan.create(planId = "sys_shutdown", planName = "Shutdown",
                steps = listOf(PcStep("SYSTEM_CMD", "SHUTDOWN"))),
            PcPlan.create(planId = "sys_restart", planName = "Restart",
                steps = listOf(PcStep("SYSTEM_CMD", "RESTART"))),
            PcPlan.create(planId = "sys_wake", planName = "Wake Screen",
                steps = listOf(
                    PcStep("SYSTEM_CMD", "WAKE_SCREEN"),
                    PcStep("WAIT", ms = 1000),
                    PcStep("KEY_PRESS", "ENTER")
                )),
            PcPlan.create(planId = "sys_unlock", planName = "Wake + Enter Password",
                steps = listOf(
                    PcStep("SYSTEM_CMD", "WAKE_SCREEN"),
                    PcStep("WAIT", ms = 1500),
                    PcStep("MOUSE_CLICK", x = 0, y = 0),
                    PcStep("WAIT", ms = 300),
                    PcStep("TYPE_TEXT", value = ""),
                    PcStep("KEY_PRESS", "ENTER")
                )),
            PcPlan.create(planId = "media_movie", planName = "Movie Night (VLC)",
                steps = listOf(
                    PcStep("LAUNCH_APP", "vlc.exe"),
                    PcStep("WAIT", ms = 2000),
                    PcStep("KEY_PRESS", "F11")
                )),
            PcPlan.create(planId = "media_ppt", planName = "Start Presentation",
                steps = listOf(
                    PcStep("LAUNCH_APP", "powerpnt.exe"),
                    PcStep("WAIT", ms = 4000),
                    PcStep("KEY_PRESS", "F5")
                )),
            PcPlan.create(planId = "prod_screenshot", planName = "Screenshot",
                steps = listOf(PcStep("SYSTEM_CMD", "SCREENSHOT"))),
            PcPlan.create(planId = "prod_mute", planName = "Toggle Mute",
                steps = listOf(PcStep("SYSTEM_CMD", "MUTE"))),
            PcPlan.create(planId = "prod_desktop", planName = "Show Desktop",
                steps = listOf(PcStep("KEY_PRESS", "WIN+D"))),
            PcPlan.create(planId = "disp_internal", planName = "PC Screen Only",
                steps = listOf(PcStep("SYSTEM_CMD", "DISPLAY_INTERNAL"))),
            PcPlan.create(planId = "disp_clone", planName = "Duplicate Display",
                steps = listOf(PcStep("SYSTEM_CMD", "DISPLAY_CLONE"))),
            PcPlan.create(planId = "disp_extend", planName = "Extend Display",
                steps = listOf(PcStep("SYSTEM_CMD", "DISPLAY_EXTEND"))),
            PcPlan.create(planId = "disp_external", planName = "Second Screen Only",
                steps = listOf(PcStep("SYSTEM_CMD", "DISPLAY_EXTERNAL")))
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

    // ── Saved devices ─────────────────────────────────────────

    val savedDevices: Flow<List<PcSavedDevice>> =
        deviceDao?.getAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun saveDevice(d: PcSavedDevice) { deviceDao?.upsert(d) }
    suspend fun deleteDevice(d: PcSavedDevice) { deviceDao?.delete(d) }
    suspend fun touchDevice(id: String) { deviceDao?.touch(id) }
    suspend fun markDeviceOnline(id: String, pcName: String) {
        deviceDao?.markOnline(id, System.currentTimeMillis(), pcName)
    }
    suspend fun findDeviceByAddress(host: String, port: Int): PcSavedDevice? =
        deviceDao?.findByHostPort(host, port)

    suspend fun findDeviceById(id: String): PcSavedDevice? = deviceDao?.findById(id)

    suspend fun setDeviceThumbnail(id: String, path: String?, ts: Long = System.currentTimeMillis()) {
        deviceDao?.setThumbnail(id, path, ts)
    }
}