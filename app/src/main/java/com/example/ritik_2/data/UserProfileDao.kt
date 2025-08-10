package com.example.ritik_2.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE userId = :userId")
    suspend fun getUserProfile(userId: String): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE userId = :userId")
    fun getUserProfileFlow(userId: String): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Update
    suspend fun updateUserProfile(profile: UserProfile)

    @Delete
    suspend fun deleteUserProfile(profile: UserProfile)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteUserProfileById(userId: String)
}
