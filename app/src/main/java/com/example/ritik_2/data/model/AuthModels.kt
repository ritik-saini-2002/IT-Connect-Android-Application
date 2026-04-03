package com.example.ritik_2.data.model

data class AuthCredentials(
    val email    : String,
    val password : String
)

data class AuthSession(
    val userId      : String,
    val token       : String,
    val email       : String,
    val name        : String,
    val role        : String,
    val documentPath: String
)

/**
 * Fields collected on the REGISTRATION screen (matches RegistrationScreen.kt exactly):
 *   name, email, password, phoneNumber, designation, companyName, department, role, imageBytes
 *
 * Fields NOT collected at registration (filled in ProfileCompletionActivity):
 *   address, employeeId, reportingTo, salary, experience, emergencyContact*
 */
data class RegistrationRequest(
    // ── Collected at registration ──────────────────────────────────────────
    val email        : String,
    val password     : String,
    val name         : String,
    val phoneNumber  : String    = "",
    val designation  : String    = "",   // collected at registration
    val companyName  : String,
    val department   : String,
    val role         : String,
    val imageBytes   : ByteArray? = null,

    // ── NOT collected at registration — zeroed, filled in ProfileCompletion ──
    val experience        : Int = 0,
    val completedProjects : Int = 0,
    val activeProjects    : Int = 0,
    val complaints        : Int = 0,

    // Always true on self-registration; also true when admin creates a user
    // (admin-created users still see ProfileCompletion on first login)
    val needsProfileCompletion: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegistrationRequest) return false
        return email == other.email && name == other.name
    }
    override fun hashCode() = 31 * email.hashCode() + name.hashCode()
}