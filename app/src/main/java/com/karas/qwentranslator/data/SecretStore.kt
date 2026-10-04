package com.karas.qwentranslator.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) : ApiKeyStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun setApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            check(prefs.edit().remove(API_KEY_PAYLOAD).commit()) { "Не удалось сохранить ключ" }
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val payload = listOf(cipher.iv, ciphertext)
            .joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
        check(prefs.edit().putString(API_KEY_PAYLOAD, payload).commit()) { "Не удалось сохранить ключ" }
    }

    @Synchronized
    override fun getApiKey(): String? {
        val payload = prefs.getString(API_KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val parts = payload.split('.', limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            require(iv.size == 12)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            require(ciphertext.size >= 16)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = loadKeyStore().getKey(KEY_ALIAS, null) as? SecretKey
                ?: error("Ключ шифрования отсутствует")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrElse {
            prefs.edit().remove(API_KEY_PAYLOAD).commit()
            null
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "translator_secrets"
        private const val API_KEY_PAYLOAD = "groq_api_key_encrypted"
        private const val KEY_ALIAS = "qwen_translator_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
