package com.example.ritik_2.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkStatsDao {
    @Query("SELECT * FROM work_stats WHERE userId = :userId")
    suspend fun getWorkStats(userId: String): WorkStats?

    @Query("SELECT * FROM work_stats WHERE userId = :userId")
    fun getWorkStatsFlow(userId: String): Flow<WorkStats?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkStats(workStats: WorkStats)

    @Update
    suspend fun updateWorkStats(workStats: WorkStats)

    @Query("UPDATE work_stats SET completed_projects = completed_projects + 1 WHERE userId = :userId")
    suspend fun incrementCompletedProjects(userId: String)

    @Query("UPDATE work_stats SET active_projects = active_projects + 1 WHERE userId = :userId")
    suspend fun incrementActiveProjects(userId: String)

    @Query("UPDATE work_stats SET completed_tasks = completed_tasks + 1 WHERE userId = :userId")
    suspend fun incrementCompletedTasks(userId: String)

    @Delete
    suspend fun deleteWorkStats(workStats: WorkStats)
}