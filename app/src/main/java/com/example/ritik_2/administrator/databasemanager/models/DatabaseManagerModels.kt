package com.example.ritik_2.administrator.databasemanager.models

enum class DBTab { USERS, DEPARTMENTS, COMPANIES, COLLECTIONS }

data class DBRecord(
    val id           : String,
    val title        : String,
    val sub1         : String,
    val sub2         : String,
    val badge        : String,
    val extra        : Map<String, String> = emptyMap(),
    // Collection-specific
    val collectionId : String = "",
    val fieldsJson   : String = "[]",
    val indexesJson  : String = "[]",
    // Raw JSON for editing
    val rawJson      : String = ""
)

data class CollectionRules(
    val listRule   : String = "",
    val viewRule   : String = "",
    val createRule : String = "",
    val updateRule : String = "",
    val deleteRule : String = ""
)

data class DBIndex(
    val name   : String,
    val type   : String = "index",   // "index" | "unique" | "spatial"
    val fields : List<String>,
    val unique : Boolean = false
)

data class DBField(
    val name     : String,
    val type     : String,   // text, number, bool, email, url, json, relation, file
    val required : Boolean = false
)

data class DBUiState(
    val isLoading    : Boolean        = false,
    val currentTab   : DBTab          = DBTab.USERS,
    val records      : List<DBRecord> = emptyList(),
    val searchQuery  : String         = "",
    val totalCount   : Int            = 0,
    val error        : String?        = null,
    val successMsg   : String?        = null,
    val adminCompany : String         = "",
    val isOffline    : Boolean        = false
)