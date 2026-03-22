package com.example.ritik_2.administrator.administratorpanel.databasemanager.module

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ritik_2.data.Company

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies ORDER BY original_name ASC")
    suspend fun getAllCompanies(): List<Company>

    @Query("SELECT * FROM companies WHERE original_name LIKE :query")
    suspend fun searchCompanies(query: String): List<Company>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanies(companies: List<Company>)

    @Query("DELETE FROM companies WHERE sanitizedName = :companyId")
    suspend fun deleteCompany(companyId: String)

    @Query("DELETE FROM companies")
    suspend fun deleteAll()
}