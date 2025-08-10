package com.example.ritik_2.administrator.administratorpanel.databasemanager.module

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ritik_2.data.Department

@Dao
interface DepartmentDao {
    @Query("SELECT * FROM departments ORDER BY department_name ASC")
    suspend fun getAllDepartments(): List<Department>

    @Query("SELECT * FROM departments WHERE sanitized_company_name = :companyName ORDER BY department_name ASC")
    suspend fun getDepartmentsByCompany(companyName: String): List<Department>

    @Query("SELECT * FROM departments WHERE department_name LIKE :query OR company_name LIKE :query")
    suspend fun searchDepartments(query: String): List<Department>

    @Query("SELECT * FROM departments WHERE id = :departmentId")
    suspend fun getDepartmentById(departmentId: String): Department?

    @Query("SELECT COUNT(*) FROM departments WHERE sanitized_company_name = :companyName")
    suspend fun getDepartmentCountByCompany(companyName: String): Int

    @Query("SELECT SUM(user_count) FROM departments WHERE sanitized_company_name = :companyName")
    suspend fun getTotalUsersByCompany(companyName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartment(department: Department)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartments(departments: List<Department>)

    @Query("UPDATE departments SET user_count = :userCount, active_users = :activeUsers, last_updated = :lastUpdated WHERE id = :departmentId")
    suspend fun updateDepartmentStats(departmentId: String, userCount: Int, activeUsers: Int, lastUpdated: Long)

    @Query("UPDATE departments SET total_complaints = :totalComplaints, open_complaints = :openComplaints, last_complaint_date = :lastComplaintDate WHERE id = :departmentId")
    suspend fun updateComplaintStats(departmentId: String, totalComplaints: Int, openComplaints: Int, lastComplaintDate: Long?)

    @Query("DELETE FROM departments WHERE id = :departmentId")
    suspend fun deleteDepartment(departmentId: String)

    @Query("DELETE FROM departments WHERE sanitized_company_name = :companyName")
    suspend fun deleteDepartmentsByCompany(companyName: String)

    @Query("DELETE FROM departments")
    suspend fun deleteAll()

    // Advanced queries for analytics
    @Query("SELECT * FROM departments WHERE sanitized_company_name = :companyName ORDER BY user_count DESC LIMIT :limit")
    suspend fun getTopDepartmentsByUserCount(companyName: String, limit: Int): List<Department>

    @Query("SELECT * FROM departments WHERE sanitized_company_name = :companyName AND total_complaints > 0 ORDER BY open_complaints DESC")
    suspend fun getDepartmentsWithComplaints(companyName: String): List<Department>

    @Query("SELECT AVG(user_count) FROM departments WHERE sanitized_company_name = :companyName")
    suspend fun getAverageUsersPerDepartment(companyName: String): Double

    @Query("SELECT * FROM departments WHERE user_count = 0")
    suspend fun getEmptyDepartments(): List<Department>

    @Query("SELECT department_name, user_count FROM departments WHERE sanitized_company_name = :companyName ORDER BY user_count DESC")
    suspend fun getDepartmentUserDistribution(companyName: String): Map<String, Int>
}