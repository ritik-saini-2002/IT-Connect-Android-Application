package com.example.ritik_2.administrator.administratorpanel.databasemanager.module

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import com.example.ritik_2.data.Complaint

@Dao
interface ComplaintDao {
    @Query("SELECT * FROM complaints ORDER BY created_at DESC")
    suspend fun getAllComplaints(): List<Complaint>

    @Query("SELECT * FROM complaints WHERE title LIKE :query OR description LIKE :query OR department LIKE :query")
    suspend fun searchComplaints(query: String): List<Complaint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaints(complaints: List<Complaint>)

    @Query("DELETE FROM complaints WHERE complaintId = :complaintId")
    suspend fun deleteComplaint(complaintId: String)

    @Query("DELETE FROM complaints")
    suspend fun deleteAll()
}