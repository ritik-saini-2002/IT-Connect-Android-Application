package com.saini.ritik.data.source.dto

import io.github.agrevster.pocketbaseKotlin.models.Record
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// profile, workStats, issues are stored as JSON objects in PocketBase
// but the SDK deserializes them — declare as JsonElement to avoid
// "Expected string but got {" deserialization error
@Serializable
data class UserRecord(
    val userId                : String      = "",
    val name                  : String      = "",
    val email                 : String      = "",
    val role                  : String      = "",
    val companyName           : String      = "",
    val sanitizedCompanyName  : String      = "",
    val department            : String      = "",
    val sanitizedDepartment   : String      = "",
    val designation           : String      = "",
    val isActive              : Boolean     = true,
    val documentPath          : String      = "",
    val permissions           : String      = "[]",
    // ✅ These three are JSON objects, not strings
    val profile               : JsonElement = JsonObject(emptyMap()),
    val workStats             : JsonElement = JsonObject(emptyMap()),
    val issues                : JsonElement = JsonObject(emptyMap()),
    val needsProfileCompletion: Boolean     = true
) : Record()

@Serializable
data class AccessControlRecord(
    val userId               : String  = "",
    val name                 : String  = "",
    val email                : String  = "",
    val companyName          : String  = "",
    val sanitizedCompanyName : String  = "",
    val department           : String  = "",
    val sanitizedDepartment  : String  = "",
    val role                 : String  = "",
    val permissions          : String  = "[]",
    val isActive             : Boolean = true,
    val documentPath         : String  = ""
) : Record() {
    @kotlinx.serialization.Transient
    var recordId: String = ""
}

@Serializable
data class CompanyRecord(
    val originalName   : String = "",
    val sanitizedName  : String = "",
    val totalUsers     : Int    = 0,
    val activeUsers    : Int    = 0,
    val availableRoles : String = "[]",
    val departments    : String = "[]"
) : Record()

@Serializable
data class SearchIndexRecord(
    val userId               : String  = "",
    val name                 : String  = "",
    val email                : String  = "",
    val companyName          : String  = "",
    val sanitizedCompanyName : String  = "",
    val department           : String  = "",
    val sanitizedDepartment  : String  = "",
    val role                 : String  = "",
    val designation          : String  = "",
    val isActive             : Boolean = true,
    val searchTerms          : String  = "[]",
    val documentPath         : String  = ""
) : Record()