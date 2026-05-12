package com.saini.ritik.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App-launch biometric gate. Prompts the user for device auth (fingerprint,
 * face, or device PIN/pattern/password) every time the main screen is
 * freshly created — i.e. every time the user opens a signed-in app.
 *
 * Intentionally stateless: there is no TTL cache here. Compare to
 * [com.saini.ritik.windowscontrol.security.MasterActionGate] which
 * caches a successful unlock for 5 minutes to keep master-key admin
 * workflows smooth; the app launch gate is stricter because "open app"
 * is a discrete event the user controls.
 *
 * Fallback: when the device reports no biometric hardware *and* no screen
 * lock, [prompt] invokes [onUnavailable] so the caller can either permit
 * entry with a warning or refuse. We choose permissive here — server-side
 * session validation still gates sensitive actions.
 */
object AppLaunchGate {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    enum class Availability { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

    fun availability(context: Context): Availability =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS                -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED    -> Availability.NOT_ENROLLED
            else                                              -> Availability.UNAVAILABLE
        }

    fun prompt(
        activity     : FragmentActivity,
        title        : String = "Unlock IT Connect",
        subtitle     : String = "Confirm it's you to continue",
        onAllow      : () -> Unit,
        onDeny       : (errorMsg: String?) -> Unit = {},
        onUnavailable: () -> Unit = {},
    ) {
        when (availability(activity)) {
            Availability.UNAVAILABLE  -> { onUnavailable(); return }
            Availability.NOT_ENROLLED -> { /* try anyway — prompt will surface a clear error */ }
            Availability.AVAILABLE    -> {}
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onAllow()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onDeny(errString.toString())
            }
            override fun onAuthenticationFailed() { /* transient — framework retries */ }
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            // No explicit negative button — DEVICE_CREDENTIAL provides the fallback.
            .build()

        runCatching { BiometricPrompt(activity, executor, callback).authenticate(info) }
            .onFailure { onDeny(it.message) }
    }
}
