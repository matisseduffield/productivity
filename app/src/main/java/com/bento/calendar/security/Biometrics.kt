package com.bento.calendar.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper over androidx.biometric. Class 2 (WEAK) is deliberate: note
 * unlock guards UI access, not a crypto key, and WEAK admits face unlock on
 * devices (like the Galaxy S25) where face is class 2.
 */
object Biometrics {

    /**
     * API 28+ only: below that, androidx.biometric falls back to its own
     * FingerprintDialogFragment, an AppCompat AlertDialog that THROWS inside
     * this app's framework (non-AppCompat) theme. 28+ always uses the
     * system prompt, which has no theme requirement. API 27 devices simply
     * don't see the biometric rows.
     */
    private fun supported(): Boolean = android.os.Build.VERSION.SDK_INT >= 28

    /** Fingerprint/face is enrolled and ready. */
    fun available(context: Context): Boolean =
        supported() && BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Something can prompt — biometrics or the device PIN/pattern/password. */
    fun anyCredential(context: Context): Boolean =
        supported() && BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Show the system sheet. With [allowDeviceCredential] the device
     * PIN/pattern is offered as the built-in fallback (and the API forbids a
     * negative button); otherwise [negativeLabel] dismisses back to the
     * caller's own fallback (the app's PIN pad). [onDismiss] fires on any
     * non-success end (negative button, back, too many attempts) — never
     * treat it as failure beyond closing the prompt.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        negativeLabel: String = "Use PIN",
        allowDeviceCredential: Boolean = false,
        onSuccess: () -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onDismiss()
            }
            // onAuthenticationFailed (single bad read) keeps the sheet open —
            // the system handles retries; no callback needed.
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .apply {
                if (allowDeviceCredential) {
                    setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                } else {
                    setAllowedAuthenticators(BIOMETRIC_WEAK)
                    setNegativeButtonText(negativeLabel)
                }
            }
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info)
    }
}
