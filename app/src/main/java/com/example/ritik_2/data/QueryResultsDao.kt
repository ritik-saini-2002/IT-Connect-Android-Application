//package com.example.ritik_2.data
//
//import androidx.room.ColumnInfo
//
//// For analytics queries
//data class ComplaintUrgencyCount(
//    val urgency: String,
//    val count: Int
//)
//
//data class UserDepartmentCount(
//    val department: String,
//    @ColumnInfo(name = "user_count") val userCount: Int
//)
//
//data class ComplaintStatusCount(
//    val status: String,
//    val count: Int
//)
//
//// For complex joins
//data class UserWithProfile(
//    val user: User,
//    val profile: UserProfile?,
//    val workStats: WorkStats?
//)
//
//data class ComplaintWithUserInfo(
//    val complaint: Complaint,
//    val userName: String,
//    val userEmail: String,
//    val userDepartment: String
//)