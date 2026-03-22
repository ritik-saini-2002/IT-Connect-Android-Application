// UserDao.kt
package com.example.ritik_2.administrator.administratorpanel.databasemanager.module

import androidx.room.*
import com.example.ritik_2.data.User

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM users WHERE name LIKE :query OR email LIKE :query OR department LIKE :query")
    suspend fun searchUsers(query: String): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

