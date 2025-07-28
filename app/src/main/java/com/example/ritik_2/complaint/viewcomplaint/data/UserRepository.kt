package com.example.ritik_2.complaint.viewcomplaint.data

import android.util.Log
import com.example.ritik_2.complaint.viewcomplaint.data.models.UserData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "UserRepository"
    }

    suspend fun getUserData(userId: String): UserData? {
        return try {
            Log.d(TAG, "Loading user data for ID: $userId")

            val userDoc = firestore.collection("user_access_control")
                .document(userId)
                .get()
                .await()

            if (!userDoc.exists()) {
                Log.e(TAG, "User document not found in user_access_control collection")
                return null
            }

            val data = userDoc.data
            if (data == null) {
                Log.e(TAG, "User document data is null")
                return null
            }

            // Safe user data extraction
            val userData = UserData(
                userId = (data["userId"] as? String) ?: userId,
                name = (data["name"] as? String) ?: "Unknown User",
                email = (data["email"] as? String) ?: "unknown@example.com",
                companyName = (data["companyName"] as? String) ?: "Unknown Company",
                sanitizedCompanyName = (data["sanitizedCompanyName"] as? String) ?: "",
                department = (data["department"] as? String) ?: "General",
                sanitizedDepartment = (data["sanitizedDepartment"] as? String) ?: "general",
                role = (data["role"] as? String) ?: "Employee",
                documentPath = (data["documentPath"] as? String) ?: "",
                phoneNumber = (data["phoneNumber"] as? String) ?: "",
                designation = (data["designation"] as? String) ?: ""
            )

            // Validate critical fields
            if (userData.sanitizedCompanyName.isBlank()) {
                Log.e(TAG, "Sanitized company name is blank - this will cause query issues")
                return null
            }

            Log.d(TAG, "User data loaded successfully: ${userData.name} (${userData.role})")
            userData

        } catch (e: Exception) {
            Log.e(TAG, "Error loading user data for ID: $userId", e)
            null
        }
    }

    suspend fun getAvailableEmployees(userData: UserData): List<UserData> {
        return try {
            val query = when (userData.role.lowercase()) {
                "administrator", "admin" -> {
                    // Admin can assign to anyone in company
                    firestore.collection("user_access_control")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                }
                "manager" -> {
                    // Manager can assign to department members
                    firestore.collection("user_access_control")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                        .whereEqualTo("sanitizedDepartment", userData.sanitizedDepartment)
                }
                else -> {
                    // Team Leader can assign to team members (same department, employee role)
                    firestore.collection("user_access_control")
                        .whereEqualTo("sanitizedCompanyName", userData.sanitizedCompanyName)
                        .whereEqualTo("sanitizedDepartment", userData.sanitizedDepartment)
                        .whereEqualTo("role", "Employee")
                }
            }

            val snapshot = query.get().await()
            val employees = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data
                    if (data != null && doc.id != userData.userId) {
                        UserData(
                            userId = data["userId"] as? String ?: doc.id,
                            name = data["name"] as? String ?: "Unknown User",
                            email = data["email"] as? String ?: "",
                            companyName = data["companyName"] as? String ?: "",
                            sanitizedCompanyName = data["sanitizedCompanyName"] as? String ?: "",
                            department = data["department"] as? String ?: "",
                            sanitizedDepartment = data["sanitizedDepartment"] as? String ?: "",
                            role = data["role"] as? String ?: "Employee",
                            documentPath = data["documentPath"] as? String ?: "",
                            phoneNumber = data["phoneNumber"] as? String ?: "",
                            designation = data["designation"] as? String ?: ""
                        )
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing employee document: ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "Loaded ${employees.size} available employees for ${userData.role}")
            employees

        } catch (e: Exception) {
            Log.e(TAG, "Error loading available employees", e)
            throw e
        }
    }

    suspend fun getUsersByRole(companyName: String, roles: List<String>): List<UserData> {
        return try {
            val query = firestore.collection("user_access_control")
                .whereEqualTo("sanitizedCompanyName", companyName)
                .whereIn("role", roles)

            val snapshot = query.get().await()
            val users = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data
                    if (data != null) {
                        UserData(
                            userId = data["userId"] as? String ?: doc.id,
                            name = data["name"] as? String ?: "Unknown User",
                            email = data["email"] as? String ?: "",
                            companyName = data["companyName"] as? String ?: "",
                            sanitizedCompanyName = data["sanitizedCompanyName"] as? String ?: "",
                            department = data["department"] as? String ?: "",
                            sanitizedDepartment = data["sanitizedDepartment"] as? String ?: "",
                            role = data["role"] as? String ?: "Employee",
                            documentPath = data["documentPath"] as? String ?: "",
                            phoneNumber = data["phoneNumber"] as? String ?: "",
                            designation = data["designation"] as? String ?: ""
                        )
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user document: ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "Loaded ${users.size} users with roles: $roles")
            users

        } catch (e: Exception) {
            Log.e(TAG, "Error loading users by role", e)
            throw e
        }
    }

    suspend fun getDepartmentUsers(companyName: String, departmentName: String): List<UserData> {
        return try {
            val query = firestore.collection("user_access_control")
                .whereEqualTo("sanitizedCompanyName", companyName)
                .whereEqualTo("sanitizedDepartment", departmentName)

            val snapshot = query.get().await()
            val users = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data
                    if (data != null) {
                        UserData(
                            userId = data["userId"] as? String ?: doc.id,
                            name = data["name"] as? String ?: "Unknown User",
                            email = data["email"] as? String ?: "",
                            companyName = data["companyName"] as? String ?: "",
                            sanitizedCompanyName = data["sanitizedCompanyName"] as? String ?: "",
                            department = data["department"] as? String ?: "",
                            sanitizedDepartment = data["sanitizedDepartment"] as? String ?: "",
                            role = data["role"] as? String ?: "Employee",
                            documentPath = data["documentPath"] as? String ?: "",
                            phoneNumber = data["phoneNumber"] as? String ?: "",
                            designation = data["designation"] as? String ?: ""
                        )
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing department user document: ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "Loaded ${users.size} users in department: $departmentName")
            users

        } catch (e: Exception) {
            Log.e(TAG, "Error loading department users", e)
            throw e
        }
    }

    suspend fun updateUserStats(userId: String, statsUpdate: Map<String, Any>) {
        try {
            firestore.collection("user_access_control")
                .document(userId)
                .update(statsUpdate)
                .await()

            Log.d(TAG, "User stats updated for: $userId")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating user stats", e)
            throw e
        }
    }

    suspend fun isUserActive(userId: String): Boolean {
        return try {
            val userDoc = firestore.collection("user_access_control")
                .document(userId)
                .get()
                .await()

            if (userDoc.exists()) {
                val isActive = userDoc.getBoolean("isActive") ?: true
                val lastActiveTimestamp = userDoc.getTimestamp("lastActive")

                // Consider user active if they were active in the last 30 days
                val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
                val lastActiveTime = lastActiveTimestamp?.toDate()?.time ?: 0

                isActive && lastActiveTime > thirtyDaysAgo
            } else {
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking user active status", e)
            false
        }
    }

    suspend fun updateLastActiveTime(userId: String) {
        try {
            firestore.collection("user_access_control")
                .document(userId)
                .update("lastActive", com.google.firebase.Timestamp.now())
                .await()

        } catch (e: Exception) {
            Log.w(TAG, "Could not update last active time for user: $userId", e)
        }
    }

    suspend fun getUserProfile(userId: String): Map<String, Any>? {
        return try {
            val userDoc = firestore.collection("user_access_control")
                .document(userId)
                .get()
                .await()

            if (userDoc.exists()) {
                userDoc.data
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading user profile", e)
            null
        }
    }
}