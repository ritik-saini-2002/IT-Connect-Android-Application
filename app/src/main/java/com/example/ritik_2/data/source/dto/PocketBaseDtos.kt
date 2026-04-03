package com.example.ritik_2.data.source.dto

import io.github.agrevster.pocketbaseKotlin.models.Record
import kotlinx.serialization.Serializable

@Serializable
data class UserRecord(
    val userId                : String  = "",
    val name                  : String  = "",
    val email                 : String  = "",
    val role                  : String  = "",
    val companyName           : String  = "",
    val sanitizedCompanyName  : String  = "",
    val department            : String  = "",
    val sanitizedDepartment   : String  = "",
    val designation           : String  = "",
    val isActive              : Boolean = true,
    val documentPath          : String  = "",
    val permissions           : String  = "[]",
    val profile               : String  = "{}",
    val workStats             : String  = "{}",
    val issues                : String  = "{}",
    val needsProfileCompletion: Boolean = true   // ✅ added — read directly from users record
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