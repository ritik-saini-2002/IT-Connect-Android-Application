package com.example.ritik_2.administrator.databasemanager.models

// ── Tab enum ──────────────────────────────────────────────────────────────────
enum class DBTab { USERS, DEPARTMENTS, COMPANIES, COLLECTIONS }

// ── Single record shown in the list ──────────────────────────────────────────
data class DBRecord(
    val id    : String,
    val title : String,
    val sub1  : String,
    val sub2  : String,
    val badge : String,
    val extra : Map<String, String> = emptyMap()
)

// ── UI state ──────────────────────────────────────────────────────────────────
data class DBUiState(
    val isLoading    : Boolean        = false,
    val currentTab   : DBTab          = DBTab.USERS,
    val records      : List<DBRecord> = emptyList(),
    val searchQuery  : String         = "",
    val totalCount   : Int            = 0,
    val error        : String?        = null,
    val successMsg   : String?        = null,
    val adminCompany : String         = ""
)