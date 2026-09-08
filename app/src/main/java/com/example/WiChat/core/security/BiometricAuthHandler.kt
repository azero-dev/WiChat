package com.example.WiChat.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthHandler(
    private val activity: FragmentActivity,
    private val onAuthSuccess: () -> Unit,
    private val onAuthError: (String) -> Unit,
) {
    private val executor = ContextCompat.getMainExecutor(activity)
    private val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onAuthError(errString.toString())
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }
    )

    fun authenticate() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("WiChat")
            .setSubtitle("Authenticate to access WiChat")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        biometricPrompt.authenticate(promptInfo)
    }
}