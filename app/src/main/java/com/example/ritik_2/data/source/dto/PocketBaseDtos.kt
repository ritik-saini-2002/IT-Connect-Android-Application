package com.example.ritik_2.data.source.dto

import io.github.agrevster.pocketbaseKotlin.models.Record
import kotlinx.serialization.Serializable

@Serializable
data class UserRecord(
    val userId               : String  = "",
    val name                 : String  = "",
    val email                : String  = "",   // fixed: was missing
    val role                 : String  = "",
    val companyName          : String  = "",
    val sanitizedCompanyName : String  = "",
    val department           : String  = "",
    val sanitizedDepartment  : String  = "",
    val designation          : String  = "",
    val isActive             : Boolean = true,
    val documentPath         : String  = "",
    val permissions          : String  = "[]",
    val profile              : String  = "{}",
    val workStats            : String  = "{}",
    val issues               : String  = "{}"
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
    // ✅ Extra field to hold the raw id from HTTP responses
    // (Record.id is a val and can't be set directly)
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
