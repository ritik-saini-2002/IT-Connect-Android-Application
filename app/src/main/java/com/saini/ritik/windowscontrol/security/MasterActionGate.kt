package com.saini.ritik.windowscontrol.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Phase 2.3 — gates master-key admin actions behind device authentication
 * (biometric or device credential) and caches a successful unlock for 5
 * minutes process-wide so the user isn't re-prompted for every sub-action.
 *
 * Usage (Compose):
 * ```
 *   val gate = rememberMasterActionGate()
 *   if (!gate.isUnlocked()) { LockedScreen(onUnlock = gate::prompt) }
 *   else { AdminContent(...) }
 * ```
 *
 * Fallback when no biometric sensor / no enrolled credential / no device
 * PIN is set: the gate reports `Availability.UNAVAILABLE` — callers must
 * decide whether to refuse entry or proceed with a softer warning. The
 * server-side master-key check still runs unconditionally, so permissive
 * fallback is safe (prompt is defence-in-depth against a forgotten, un-
 * locked device in someone else's hand, not the only auth factor).
 */
object MasterActionGate {

    /** Cache TTL for a successful unlock — keeps admin workflows smooth. */
    private const val CACHE_MS = 5 * 60 * 1000L

    @Volatile private var unlockedUntilMs: Long = 0L

    enum class Availability {
        /** BiometricPrompt can succeed with strong biometric or device PIN/pattern. */
        AVAILABLE,
        /** No biometric hardware AND no screen lock — prompt is impossible. */
        UNAVAILABLE,
        /** Hardware exists but user hasn't enrolled — prompt will likely fail. */
        NOT_ENROLLED,
    }

    /** Authenticators allowed: strong biometric OR device PIN/pattern/password. */
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): Availability {
        val mgr = BiometricManager.from(context)
        return when (mgr.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS                  -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED      -> Availability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED        -> Availability.UNAVAILABLE
            else                                                -> Availability.UNAVAILABLE
        }
    }

    /** True if the last successful unlock is still inside the 5-minute window. */
    fun isUnlocked(): Boolean = System.currentTimeMillis() < unlockedUntilMs

    /** Force the cache to expire — e.g. sign-out, user explicitly locks. */
    fun invalidate() { unlockedUntilMs = 0L }

    /**
     * Prompt the user for biometric/device-credential auth.
     *
     * If the device cannot authenticate (no sensor + no PIN), the `onUnavailable`
     * callback fires and the caller should decide whether to allow the action
     * with a softer warning or refuse entry. On success, `onAllow()` is invoked
     * after refreshing the cache.
     */
    fun prompt(
        activity     : FragmentActivity,
        title        : String = "Admin authentication",
        subtitle     : String = "Confirm it's you before running master-key actions",
        onAllow      : () -> Unit,
        onDeny       : (errorMsg: String?) -> Unit = {},
        onUnavailable: () -> Unit = {},
    ) {
        // Hot-path: recent unlock still valid — skip prompt.
        if (isUnlocked()) { onAllow(); return }

        when (availability(activity)) {
            Availability.UNAVAILABLE  -> { onUnavailable(); return }
            Availability.NOT_ENROLLED -> { /* prompt will surface error — still try */ }
            Availability.AVAILABLE    -> {}
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlockedUntilMs = System.currentTimeMillis() + CACHE_MS
                onAllow()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // USER_CANCELED & NEGATIVE_BUTTON are user-initiated — quiet path.
                onDeny(errString.toString())
            }
            override fun onAuthenticationFailed() {
                // Transient — framework will retry; no-op here.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            // Note: negative button is implicit when DEVICE_CREDENTIAL is an
            // allowed authenticator — providing one explicitly would crash.
            .build()

        runCatching { prompt.authenticate(info) }
            .onFailure { onDeny(it.message) }
    }
}
