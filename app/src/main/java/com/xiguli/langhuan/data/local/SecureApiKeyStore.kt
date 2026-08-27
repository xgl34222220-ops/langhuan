package com.xiguli.langhuan.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("langhuan_secure_ai", Context.MODE_PRIVATE)

    fun put(providerId: String, apiKey: String) {
        if (apiKey.isBlank()) return
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(prefKey(providerId), payload).apply()
    }

    fun get(providerId: String): String? = runCatching {
        val payload = prefs.getString(prefKey(providerId), null) ?: return@runCatching null
        val parts = payload.split('.', limit = 2)
        if (parts.size != 2) return@runCatching null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    fun has(providerId: String): Boolean = prefs.contains(prefKey(providerId))

    fun remove(providerId: String) {
        prefs.edit().remove(prefKey(providerId)).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun prefKey(providerId: String) = "provider_$providerId"

    companion object {
        private const val KEY_ALIAS = "langhuan.ai.credentials.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
