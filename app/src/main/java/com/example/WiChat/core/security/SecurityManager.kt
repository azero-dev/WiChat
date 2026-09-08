package com.example.WiChat.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("wichat_security_prefs", Context.MODE_PRIVATE)
    companion object {
        private const val KEY_ALIAS = "WiChatSecurityKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val LAST_UNLOCK_TIME_ENC = "last_unlock_time_enc"
        private const val IV_KEY = "last_unlock_iv"
        private const val ALGORITHM = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    }

    init {
        generateKeyIfNeeded()
    }

    private fun generateKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

//    check if time passed is less than timeout choosed by user
    fun isSessionValid(timeout: Long): Boolean {
        if (timeout <= 0) return false
        val lastUnlock = decryptTimestamp()
        return (System.currentTimeMillis() - lastUnlock) < timeout
    }

    fun updateUnlockTimestamp() {
        encryptTimestamp(System.currentTimeMillis())
    }

//    delete biometric info so it should be used next time again
    fun lockSession() {
        sharedPrefs.edit().remove(LAST_UNLOCK_TIME_ENC).remove(IV_KEY).apply()
    }

    private fun encryptTimestamp(timestamp: Long) {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(timestamp.toString().toByteArray())
        sharedPrefs.edit()
            .putString(LAST_UNLOCK_TIME_ENC, Base64.encodeToString(encrypted, Base64.DEFAULT))
            .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }

    private fun decryptTimestamp(): Long {
        val encryptedBase64 = sharedPrefs.getString(LAST_UNLOCK_TIME_ENC, null) ?: return 0L
        val ivBase64 = sharedPrefs.getString(IV_KEY, null) ?: return 0L
        return try {
            val encrypted = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted).toLong()
        } catch (e: Exception) {
            0L
        }
    }
}



















