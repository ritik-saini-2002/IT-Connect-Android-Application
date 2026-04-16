package com.example.ritik_2.localdatabase

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── User DAO ──────────────────────────────────────────────────────────────────
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE sanitizedCompanyName = :sc ORDER BY name ASC")
    fun observeByCompany(sc: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE sanitizedCompanyName = :sc ORDER BY name ASC")
    suspend fun getByCompany(sc: String): List<UserEntity>

    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAll(): List<UserEntity>

    @Query("""
        SELECT * FROM users WHERE sanitizedCompanyName = :sc AND (
            name        LIKE '%' || :q || '%' OR
            email       LIKE '%' || :q || '%' OR
            role        LIKE '%' || :q || '%' OR
            designation LIKE '%' || :q || '%' OR
            department  LIKE '%' || :q || '%'
        ) ORDER BY name ASC
    """)
    suspend fun search(sc: String, q: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE sanitizedCompanyName = :sc AND sanitizedDepartment = :sd")
    suspend fun getByDepartment(sc: String, sd: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE sanitizedCompanyName = :sc AND role = :role")
    suspend fun getByRole(sc: String, role: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE pendingCreate = 1 OR pendingUpdate = 1 OR pendingDelete = 1")
    suspend fun getPending(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("UPDATE users SET isActive = :active, pendingUpdate = 1 WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("UPDATE users SET role = :role, pendingUpdate = 1 WHERE id = :id")
    suspend fun setRole(id: String, role: String)

    @Query("UPDATE users SET department = :dept, sanitizedDepartment = :sd, pendingUpdate = 1 WHERE id = :id")
    suspend fun setDepartment(id: String, dept: String, sd: String)

    @Query("UPDATE users SET pendingDelete = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM users WHERE sanitizedCompanyName = :sc")
    suspend fun clearCompany(sc: String)

    @Query("SELECT COUNT(*) FROM users WHERE sanitizedCompanyName = :sc AND isActive = 1")
    suspend fun activeCount(sc: String): Int

    /** Update only the permissions list for a single user — no DB migration needed. */
    @Query("UPDATE users SET permissions = :permissions WHERE id = :id")
    suspend fun updatePermissions(id: String, permissions: List<String>)
}

// ── Role DAO ──────────────────────────────────────────────────────────────────
@Dao
interface RoleDao {
    @Query("SELECT * FROM roles WHERE sanitizedCompanyName = :sc ORDER BY name ASC")
    fun observeByCompany(sc: String): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE sanitizedCompanyName = :sc ORDER BY name ASC")
    suspend fun getByCompany(sc: String): List<RoleEntity>

    @Query("SELECT * FROM roles WHERE id = :id")
    suspend fun getById(id: String): RoleEntity?

    @Query("SELECT * FROM roles WHERE pendingCreate = 1 OR pendingDelete = 1")
    suspend fun getPending(): List<RoleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(role: RoleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(roles: List<RoleEntity>)

    @Query("UPDATE roles SET userCount = userCount + :delta WHERE id = :id")
    suspend fun adjustCount(id: String, delta: Int)

    @Query("DELETE FROM roles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM roles WHERE sanitizedCompanyName = :sc")
    suspend fun clearCompany(sc: String)
}

// ── Department DAO ─────────────────────────────────────────────────────────────
@Dao
interface DepartmentDao {
    @Query("SELECT * FROM departments WHERE sanitizedCompanyName = :sc ORDER BY name ASC")
    fun observeByCompany(sc: String): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments WHERE sanitizedCompanyName = :sc ORDER BY name ASC")
    suspend fun getByCompany(sc: String): List<DepartmentEntity>

    @Query("SELECT * FROM departments WHERE id = :id")
    suspend fun getById(id: String): DepartmentEntity?

    @Query("SELECT * FROM departments WHERE pendingCreate = 1 OR pendingDelete = 1")
    suspend fun getPending(): List<DepartmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dept: DepartmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(depts: List<DepartmentEntity>)

    @Query("UPDATE departments SET userCount = userCount + :delta WHERE id = :id")
    suspend fun adjustCount(id: String, delta: Int)

    @Query("DELETE FROM departments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM departments WHERE sanitizedCompanyName = :sc")
    suspend fun clearCompany(sc: String)
}

// ── Company DAO ───────────────────────────────────────────────────────────────
@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies ORDER BY originalName ASC")
    suspend fun getAll(): List<CompanyEntity>

    @Query("SELECT * FROM companies WHERE sanitizedName = :sc")
    suspend fun getByName(sc: String): CompanyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(company: CompanyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(companies: List<CompanyEntity>)
}

// ── Collection DAO ────────────────────────────────────────────────────────────
@Dao
interface CollectionDao {
    @Query("SELECT * FROM pb_collections ORDER BY name ASC")
    suspend fun getAll(): List<CollectionEntity>

    @Query("SELECT * FROM pb_collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(col: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cols: List<CollectionEntity>)

    @Query("DELETE FROM pb_collections WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pb_collections")
    suspend fun clear()
}

// ── Sync Queue DAO ────────────────────────────────────────────────────────────
@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Insert
    suspend fun enqueue(op: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE queueId = :id")
    suspend fun dequeue(id: Int)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :err WHERE queueId = :id")
    suspend fun markFailed(id: Int, err: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}