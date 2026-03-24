package com.example.ritik_2.complaint.newcomplaintregistration.models

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────
// FORM DATA MODEL
// ─────────────────────────────────────────────

/**
 * Plain data class that the UI fills and passes to the Activity for saving.
 * No Android/Compose dependencies — pure data.
 */
data class ComplaintFormData(
    val title: String,
    val description: String,
    val category: String,
    val urgency: String,
    val contactInfo: String,
    val hasAttachment: Boolean,
    val isGlobal: Boolean
)

// ─────────────────────────────────────────────
// UI STATE MODEL
// ─────────────────────────────────────────────

/**
 * Snapshot of everything the screen needs to render.
 * Emitted by ComplaintFormViewModel as a single StateFlow.
 */
data class ComplaintFormUiState(
    // User info (loaded from Firestore)
    val userName: String = "",
    val userRole: String = "",
    val userDept: String = "",
    val profilePictureUrl: String? = null,
    val profileLoaded: Boolean = false,

    // Form fields
    val title: String = "",
    val description: String = "",
    val selectedCategory: String = "Technical",
    val selectedUrgency: String = "Medium",
    val contactInfo: String = "",
    val hasAttachment: Boolean = false,
    val isGlobal: Boolean = false,

    // Submission state
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submitSuccess: Boolean = false
) {
    val isFormValid: Boolean
        get() = title.isNotBlank() && description.isNotBlank()

    val estimatedResolutionTime: String
        get() = when (selectedUrgency) {
            "Critical" -> "4 hours"
            "High"     -> "24 hours"
            "Medium"   -> "3 days"
            else       -> "1 week"
        }
}

// ─────────────────────────────────────────────
// STATIC COLOR HELPERS
// (non-@Composable — safe to call from ViewModel / Activity)
// ─────────────────────────────────────────────

fun urgencyColorStatic(urgency: String): Color = when (urgency.lowercase()) {
    "critical" -> Color(0xFFFF0000)
    "high"     -> Color(0xFFFF6600)
    "medium"   -> Color(0xFFFFB300)
    "low"      -> Color(0xFF4CAF50)
    else       -> Color(0xFF9E9E9E)
}

fun avatarColorsStatic(seed: Int): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFF6366F1) to Color(0xFF8B5CF6),
        Color(0xFF10B981) to Color(0xFF059669),
        Color(0xFFF59E0B) to Color(0xFFD97706),
        Color(0xFFEF4444) to Color(0xFFDC2626),
        Color(0xFF3B82F6) to Color(0xFF2563EB),
        Color(0xFF8B5CF6) to Color(0xFF7C3AED),
        Color(0xFFF97316) to Color(0xFFEA580C),
        Color(0xFF14B8A6) to Color(0xFF0D9488)
    )
    return palette[Math.abs(seed) % palette.size]
}

// ─────────────────────────────────────────────
// STATIC BUSINESS LOGIC HELPERS
// (pure functions — no Android context needed)
// ─────────────────────────────────────────────

/** Maps the UI category label to the Firestore department name. */
val categoryToDeptMap = mapOf(
    "Technical"      to "Technical",
    "HR"             to "Human Resources",
    "Administrative" to "Administration",
    "IT Support"     to "IT Support",
    "Finance"        to "Finance",
    "General"        to "General"
)

fun mapCategoryToDepartment(category: String): String =
    categoryToDeptMap[category] ?: category

fun calculatePriority(urgency: String, departmentName: String): Int {
    val urgencyScore = when (urgency) {
        "Critical" -> 4; "High" -> 3; "Medium" -> 2; else -> 1
    }
    val categoryScore = when (departmentName) {
        "Technical", "IT Support" -> 1; else -> 0
    }
    return urgencyScore + categoryScore
}

fun estimatedResolutionTime(urgency: String): String = when (urgency) {
    "Critical" -> "4 hours"
    "High"     -> "24 hours"
    "Medium"   -> "3 days"
    else       -> "1 week"
}

fun sanitizeComplaintId(title: String): String =
    title.trim()
        .replace(Regex("[^a-zA-Z0-9\\s\\-_]"), "")
        .replace(Regex("\\s+"), "_")
        .lowercase()
        .take(80)
        .ifBlank { "complaint_${System.currentTimeMillis()}" }

fun generateTags(title: String, description: String, dept: String): List<String> {
    val stopWords = setOf(
        "the", "and", "or", "but", "in", "on", "at", "to",
        "for", "of", "with", "by", "a", "an", "is", "are"
    )
    return "$title $description $dept".lowercase()
        .split(Regex("\\s+"))
        .filter { it.length > 2 && it !in stopWords }
        .distinct()
        .take(10)
}

fun createSearchTerms(
    title: String,
    description: String,
    dept: String,
    urgency: String
): List<String> = listOf(title, description, dept, urgency)
    .flatMap { it.lowercase().split(Regex("\\s+")) }
    .filter { it.length > 2 }
    .distinct()
