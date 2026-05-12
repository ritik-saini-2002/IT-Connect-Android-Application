package com.saini.ritik.core

object StringUtils {
    /** Converts any string to a safe PocketBase collection-path segment */
    fun sanitize(input: String): String =
        input.trim()
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(100)
            .ifEmpty { "unknown" }

    /** Validates email format */
    fun isValidEmail(email: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** Validates password meets minimum requirements */
    fun isValidPassword(password: String): Boolean = password.length >= 8
}