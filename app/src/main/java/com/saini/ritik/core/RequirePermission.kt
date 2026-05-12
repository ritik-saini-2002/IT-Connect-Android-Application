package com.saini.ritik.core

import android.widget.Toast
import androidx.activity.ComponentActivity
import com.saini.ritik.auth.AuthRepository

/**
 * Defensive Activity-level permission gate. Use this in [ComponentActivity.onCreate]
 * BEFORE calling setContent { ... } to prevent users without the required permission
 * from reaching the screen via direct intent launch, deep link, notification tap,
 * or any other entry point that bypasses the dashboard card guard.
 *
 * On denial:
 *  - Shows a Toast with [deniedMessage]
 *  - Calls [ComponentActivity.finish]
 *  - Returns `false` so the caller can `return` immediately and skip setContent
 *
 * Returns `true` if access is allowed and the Activity should proceed.
 *
 * Example:
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     if (!requirePermission(authRepository,
 *             rule = { role, _, dba -> PermissionGuard.canAccessAdminPanel(role, dba) },
 *             deniedMessage = "Admin Panel access denied"))
 *         return
 *     setContent { ... }
 * }
 * ```
 *
 * The session is read from [AuthRepository] via the standard accessors so this
 * helper never bypasses any session/role logic that already lives there.
 */
fun ComponentActivity.requirePermission(
    authRepository: AuthRepository,
    rule          : (role: String, permissions: List<String>, isDbAdmin: Boolean) -> Boolean,
    deniedMessage : String = "You don't have permission to access this screen"
): Boolean {
    val session = authRepository.getSession()
    if (session == null) {
        // Defensive — onCreate should not run with no session. Bounce back.
        finish()
        return false
    }
    val allowed = rule(session.role, session.permissions, authRepository.isDbAdmin())
    if (!allowed) {
        Toast.makeText(this, deniedMessage, Toast.LENGTH_LONG).show()
        finish()
        return false
    }
    return true
}
