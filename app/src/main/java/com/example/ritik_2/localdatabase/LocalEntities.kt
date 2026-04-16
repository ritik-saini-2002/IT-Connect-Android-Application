package com.example.ritik_2.localdatabase

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.ritik_2.data.model.Permissions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ── Type converters ───────────────────────────────────────────────────────────
class Converters {
    private val gson = Gson()
    @TypeConverter fun fromStringList(v: List<String>): String = gson.toJson(v)
    @TypeConverter fun toStringList(v: String): List<String> =
        gson.fromJson(v, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    @TypeConverter fun fromStringMap(v: Map<String, String>): String = gson.toJson(v)
    @TypeConverter fun toStringMap(v: String): Map<String, String> =
        gson.fromJson(v, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
}

/**
 * Extension on UserEntity: returns ALL system permissions as Map<String, Boolean>.
 * true  = this user has the permission
 * false = this user does NOT have the permission
 *
 * This is computed on-the-fly from the stored permissions List<String>.
 * No DB migration required — the list is the source of truth.
 */
val UserEntity.permissionsMap: Map<String, Boolean>
    get() = Permissions.ALL_PERMISSIONS.associateWith { it in permissions }

/**
 * Extension on RoleEntity: same pattern as UserEntity.permissionsMap.
 */
val RoleEntity.permissionsMap: Map<String, Boolean>
    get() = Permissions.ALL_PERMISSIONS.associateWith { it in permissions }

// ── User cache ────────────────────────────────────────────────────────────────
@Entity(tableName = "users")
@TypeConverters(Converters::class)
data class UserEntity(
    @PrimaryKey val id                      : String,
    val name                                : String  = "",
    val email                               : String  = "",
    val role                                : String  = "",
    val companyName                         : String  = "",
    val sanitizedCompanyName                : String  = "",
    val department                          : String  = "",
    val sanitizedDepartment                 : String  = "",
    val designation                         : String  = "",
    val imageUrl                            : String  = "",
    val phoneNumber                         : String  = "",
    val address                             : String  = "",
    val employeeId                          : String  = "",
    val reportingTo                         : String  = "",
    val salary                              : Double  = 0.0,
    val experience                          : Int     = 0,
    val completedProjects                   : Int     = 0,
    val activeProjects                      : Int     = 0,
    val pendingTasks                        : Int     = 0,
    val completedTasks                      : Int     = 0,
    val totalComplaints                     : Int     = 0,
    val resolvedComplaints                  : Int     = 0,
    val pendingComplaints                   : Int     = 0,
    val isActive                            : Boolean = true,
    val documentPath                        : String  = "",
    val permissions                         : List<String> = emptyList(),
    val emergencyContactName                : String  = "",
    val emergencyContactPhone               : String  = "",
    val emergencyContactRelation            : String  = "",
    val needsProfileCompletion              : Boolean = true,
    val cachedAt                            : Long    = System.currentTimeMillis(),
    // Pending sync flags
    val pendingUpdate                       : Boolean = false,
    val pendingCreate                       : Boolean = false,
    val pendingDelete                       : Boolean = false
)

// ── Role cache ────────────────────────────────────────────────────────────────
@Entity(tableName = "roles")
@TypeConverters(Converters::class)
data class RoleEntity(
    @PrimaryKey val id          : String,
    val name                    : String,
    val sanitizedCompanyName    : String,
    val companyName             : String,
    val permissions             : List<String> = emptyList(),
    val userCount               : Int          = 0,
    val isCustom                : Boolean      = false,  // false = built-in role
    val cachedAt                : Long         = System.currentTimeMillis(),
    val pendingCreate           : Boolean      = false,
    val pendingDelete           : Boolean      = false
)

// ── Department cache ──────────────────────────────────────────────────────────
@Entity(tableName = "departments")
@TypeConverters(Converters::class)
data class DepartmentEntity(
    @PrimaryKey val id          : String,
    val name                    : String,
    val sanitizedName           : String,
    val companyName             : String,
    val sanitizedCompanyName    : String,
    val availableRoles          : List<String> = emptyList(),
    val userCount               : Int          = 0,
    val activeUsers             : Int          = 0,
    val cachedAt                : Long         = System.currentTimeMillis(),
    val pendingCreate           : Boolean      = false,
    val pendingDelete           : Boolean      = false
)

// ── Company cache ─────────────────────────────────────────────────────────────
@Entity(tableName = "companies")
@TypeConverters(Converters::class)
data class CompanyEntity(
    @PrimaryKey val sanitizedName : String,
    val originalName              : String,
    val totalUsers                : Int          = 0,
    val activeUsers               : Int          = 0,
    val availableRoles            : List<String> = emptyList(),
    val departments               : List<String> = emptyList(),
    val cachedAt                  : Long         = System.currentTimeMillis()
)

// ── Collection metadata cache (for Database Manager) ─────────────────────────
@Entity(tableName = "pb_collections")
@TypeConverters(Converters::class)
data class CollectionEntity(
    @PrimaryKey val id    : String,
    val name              : String,
    val type              : String,
    val listRule          : String = "",
    val viewRule          : String = "",
    val createRule        : String = "",
    val updateRule        : String = "",
    val deleteRule        : String = "",
    val fields            : String = "[]",   // JSON array string
    val indexes           : String = "[]",   // JSON array string
    val cachedAt          : Long   = System.currentTimeMillis()
)

// ── Pending operations queue ──────────────────────────────────────────────────
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId : Int    = 0,
    val operationType : String,   // CREATE, UPDATE, DELETE, ROLE_CHANGE, MOVE_USER
    val collection    : String,
    val recordId      : String,
    val payload       : String,   // JSON string
    val createdAt     : Long      = System.currentTimeMillis(),
    val retryCount    : Int       = 0,
    val lastError     : String    = ""
)