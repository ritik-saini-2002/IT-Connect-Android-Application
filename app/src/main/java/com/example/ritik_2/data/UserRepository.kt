//package com.example.ritik_2.data
//
//import com.example.ritik_2.administrator.administratorpanel.databasemanager.module.UserDao
//import kotlinx.coroutines.flow.Flow
//import javax.inject.Inject
//import javax.inject.Singleton
//
//@Singleton
//class LocalUserRepository @Inject constructor(
//    private val userDao: UserDao
//) {
//
//    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()
//
//    suspend fun getUserById(userId: String): User? = userDao.getUserById(userId)
//
//    suspend fun insertUser(user: User) = userDao.insertUser(user)
//
//    suspend fun updateUser(user: User) = userDao.updateUser(user)
//
//    suspend fun deleteUser(userId: String) = userDao.deleteUserById(userId)
//
//    fun searchUsers(query: String): Flow<List<User>> = userDao.searchUsers(query)
//}