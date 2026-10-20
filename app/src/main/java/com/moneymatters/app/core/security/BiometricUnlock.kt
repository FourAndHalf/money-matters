package com.moneymatters.app.core.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Biometric unlock through the platform [BiometricPrompt] (Android 9+), so no extra library or
 * FragmentActivity is needed. On Android 8, or with nothing enrolled, [isAvailable] is false and
 * the lock screen is passcode-only.
 */
class BiometricUnlock(private val context: Context) {

    fun isAvailable(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            context.getSystemService(BiometricManager::class.java)
                ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            @Suppress("DEPRECATION")
            (context.getSystemService(BiometricManager::class.java)?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
        // Android 9 has no availability query; the prompt itself reports "none enrolled" as an error.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> true
        else -> false
    }

    /** Shows the system prompt. [onFailed] covers cancel, "Use passcode" and errors alike: fall back to the keypad. */
    fun prompt(onSuccess: () -> Unit, onFailed: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return onFailed()
        val executor = context.mainExecutor
        val builder = BiometricPrompt.Builder(context)
            .setTitle("Unlock Money Matters")
            .setNegativeButton("Use passcode", executor) { _, _ -> onFailed() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        }
        builder.build().authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailed()
            },
        )
    }
}
