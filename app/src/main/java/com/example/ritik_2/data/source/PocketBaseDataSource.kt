package com.example.ritik_2.data.source

import android.util.Log
import com.example.ritik_2.pocketbase.PocketBaseClient
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_ACCESS_CONTROL
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_COMPANIES
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_SEARCH_INDEX
import com.example.ritik_2.pocketbase.PocketBaseInitializer.COL_USERS
import com.example.ritik_2.registration.models.AccessControlRecord
import com.example.ritik_2.registration.models.CompanyRecord
import com.example.ritik_2.registration.models.SearchIndexRecord
import com.example.ritik_2.registration.models.UserRecord
import io.github.agrevster.pocketbaseKotlin.FileUpload
import io.github.agrevster.pocketbaseKotlin.dsl.login
import io.github.agrevster.pocketbaseKotlin.dsl.logout
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import io.github.agrevster.pocketbaseKotlin.models.AuthRecord
import io.github.agrevster.pocketbaseKotlin.toJsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PocketBaseDataSource : AppDataSource {

    private val pb  = PocketBaseClient.instance
    private val TAG = "PBDataSource"

    // ── Auth ──────────────────────────────────────────────────
    override suspend fun login(email: String, password: String): AuthResult {
        return withContext(Dispatchers.IO) {
            val response = pb.records.authWithPassword<AuthRecord>(COL_USERS, email, password)
            val token  = response.token  ?: throw Exception("No token received")
            val userId = response.record?.id ?: throw Exception("No user ID received")
            pb.login { this.token = token }
            AuthResult(userId = userId, token = token, email = email)
        }
    }

    override suspend fun logout() {
        pb.logout()
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                pb.records.requestPasswordReset(COL_USERS, email)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun createUser(email: String, password: String, name: String): String {
        return withContext(Dispatchers.IO) {
            val response = pb.records.create<UserRecord>(
                COL_USERS,
                body = mapOf(
                    "email"           to email.toJsonPrimitive(),
                    "password"        to password.toJsonPrimitive(),
                    "passwordConfirm" to password.toJsonPrimitive(),
                    "name"            to name.toJsonPrimitive()
                ).toString()
            )
            response.id ?: throw Exception("No user ID in response")
        }
    }

    override suspend fun restoreSession(token: String) {
        pb.login { this.token = token }
    }

    // ── User Records ──────────────────────────────────────────
    override suspend fun updateUserRecord(userId: String, fields: Map<String, Any>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                pb.records.update<UserRecord>(
                    COL_USERS, userId,
                    body = fields.mapValues { (_, v) ->
                        when (v) {
                            is String  -> v.toJsonPrimitive()
                            is Int     -> v.toJsonPrimitive()
                            is Boolean -> v.toJsonPrimitive()
                            is Double  -> v.toJsonPrimitive()
                            is Long    -> v.toJsonPrimitive()
                            else       -> v.toString().toJsonPrimitive()
                        }
                    }.toString()
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "updateUserRecord failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun getUserRecord(userId: String): Result<UserDto> {
        return withContext(Dispatchers.IO) {
            try {
                val record = pb.records.getOne<UserRecord>(COL_USERS, userId)
                Result.success(
                    UserDto(
                        id           = record.id ?: userId,
                        name         = record.name,
                        role         = record.role,
                        companyName  = record.companyName,
                        department   = record.department,
                        designation  = record.designation,
                        isActive     = record.isActive,
                        documentPath = record.documentPath,
                        permissions  = record.permissions,
                        profile      = record.profile,
                        workStats    = record.workStats,
                        issues       = record.issues
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "getUserRecord failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun getUserAccessControl(userId: String): Result<AccessControlDto> {
        return withContext(Dispatchers.IO) {
            try {
                val result = pb.records.getList<AccessControlRecord>(
                    sub = COL_ACCESS_CONTROL,
                    page = 1,
                    perPage = 1,
                    //filter = Filter("userId='$userId'")
                )

                if (result.totalItems == 0) {
                    return@withContext Result.failure(Exception("Access control not found"))
                }

                val r = result.items.first()

                Result.success(
                    AccessControlDto(
                        id = r.id ?: "",
                        userId = r.userId,
                        name = r.name,
                        email = r.email,
                        role = r.role,
                        companyName = r.companyName,
                        sanitizedCompanyName = r.sanitizedCompanyName,
                        department = r.department,
                        sanitizedDepartment = r.sanitizedDepartment,
                        isActive = r.isActive,
                        documentPath = r.documentPath,
                        permissions = r.permissions
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "getUserAccessControl failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ── Company ───────────────────────────────────────────────
    override suspend fun companyExists(sanitizedName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val r = pb.records.getList<CompanyRecord>(
                    COL_COMPANIES, 1, 1,
                    //filter = Filter("sanitizedName='$sanitizedName'")
                )
                r.totalItems > 0
            } catch (e: Exception) { false }
        }
    }

    override suspend fun createCompany(dto: CompanyDto): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                pb.records.create<CompanyRecord>(
                    COL_COMPANIES,
                    body = mapOf(
                        "originalName"   to dto.originalName.toJsonPrimitive(),
                        "sanitizedName"  to dto.sanitizedName.toJsonPrimitive(),
                        "totalUsers"     to dto.totalUsers.toJsonPrimitive(),
                        "activeUsers"    to dto.activeUsers.toJsonPrimitive(),
                        "availableRoles" to dto.availableRoles.toJsonPrimitive(),
                        "departments"    to dto.departments.toJsonPrimitive()
                    ).toString()
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "createCompany failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun incrementCompanyUsers(
        companyId: String, currentTotal: Int, currentActive: Int
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                pb.records.update<CompanyRecord>(
                    COL_COMPANIES, companyId,
                    body = mapOf(
                        "totalUsers"  to (currentTotal  + 1).toJsonPrimitive(),
                        "activeUsers" to (currentActive + 1).toJsonPrimitive()
                    ).toString()
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ── Access Control ────────────────────────────────────────
    override suspend fun createAccessControl(dto: AccessControlDto): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                pb.records.create<AccessControlRecord>(
                    COL_ACCESS_CONTROL,
                    body = mapOf(
                        "userId"               to dto.userId.toJsonPrimitive(),
                        "name"                 to dto.name.toJsonPrimitive(),
                        "email"                to dto.email.toJsonPrimitive(),
                        "companyName"          to dto.companyName.toJsonPrimitive(),
                        "sanitizedCompanyName" to dto.sanitizedCompanyName.toJsonPrimitive(),
                        "department"           to dto.department.toJsonPrimitive(),
                        "sanitizedDepartment"  to dto.sanitizedDepartment.toJsonPrimitive(),
                        "role"                 to dto.role.toJsonPrimitive(),
                        "permissions"          to dto.permissions.toJsonPrimitive(),
                        "isActive"             to true.toJsonPrimitive(),
                        "documentPath"         to dto.documentPath.toJsonPrimitive()
                    ).toString()
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ── Search Index ──────────────────────────────────────────
    override suspend fun createSearchIndex(dto: SearchIndexDto): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                pb.records.create<SearchIndexRecord>(
                    COL_SEARCH_INDEX,
                    body = mapOf(
                        "userId"               to dto.userId.toJsonPrimitive(),
                        "name"                 to dto.name.toJsonPrimitive(),
                        "email"                to dto.email.toJsonPrimitive(),
                        "companyName"          to dto.companyName.toJsonPrimitive(),
                        "sanitizedCompanyName" to dto.sanitizedCompanyName.toJsonPrimitive(),
                        "department"           to dto.department.toJsonPrimitive(),
                        "sanitizedDepartment"  to dto.sanitizedDepartment.toJsonPrimitive(),
                        "role"                 to dto.role.toJsonPrimitive(),
                        "designation"          to dto.designation.toJsonPrimitive(),
                        "isActive"             to true.toJsonPrimitive(),
                        "searchTerms"          to dto.searchTerms.toJsonPrimitive(),
                        "documentPath"         to dto.documentPath.toJsonPrimitive()
                    ).toString()
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ── File Upload ───────────────────────────────────────────
    override suspend fun uploadProfileImage(
        userId: String, bytes: ByteArray, filename: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                pb.records.update<UserRecord>(
                    COL_USERS, userId,
                    body  = mapOf("name" to userId.toJsonPrimitive()),
                    files = listOf(FileUpload("profileImage", bytes, filename))
                )
                val url = "${PocketBaseClient.BASE_URL}/api/files/$COL_USERS/$userId/$filename"
                Result.success(url)
            } catch (e: Exception) {
                Log.e(TAG, "uploadProfileImage failed: ${e.message}")
                Result.failure(e)
            }
        }
    }
}