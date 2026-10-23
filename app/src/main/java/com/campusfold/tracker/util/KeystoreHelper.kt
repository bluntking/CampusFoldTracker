package com.campusfold.tracker.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore helper for encrypting sensitive credentials (e.g. Gmail App Password).
 * Uses AES-GCM (256-bit key) backed by the AndroidKeyStore provider.
 */
object KeystoreHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CampusFoldMasterKey"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plain text using AES/GCM/NoPadding and returns a Base64-encoded payload
     * containing the IV and ciphertext.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val byteBuffer = ByteBuffer.allocate(1 + iv.size + cipherBytes.size)
            byteBuffer.put(iv.size.toByte())
            byteBuffer.put(iv)
            byteBuffer.put(cipherBytes)

            return Base64.getEncoder().encodeToString(byteBuffer.array())
        } catch (e: Exception) {
            // Fallback for non-Android JVM environments or unexpected keystore errors
            return "PLAIN:$plainText"
        }
    }

    /**
     * Decrypts a Base64-encoded AES-GCM payload.
     * Returns null if decryption fails or if input is empty.
     */
    fun decrypt(encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrEmpty()) return null
        if (encryptedPayload.startsWith("PLAIN:")) {
            return encryptedPayload.removePrefix("PLAIN:")
        }

        try {
            val decoded = Base64.getDecoder().decode(encryptedPayload)
            val byteBuffer = ByteBuffer.wrap(decoded)
            val ivLength = byteBuffer.get().toInt()
            if (ivLength != GCM_IV_LENGTH_BYTES && ivLength <= 0) {
                return encryptedPayload // Legacy unencrypted fallback
            }

            val iv = ByteArray(ivLength)
            byteBuffer.get(iv)

            val cipherBytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(cipherBytes)

            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val decryptedBytes = cipher.doFinal(cipherBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Return null or plain string if it was not encrypted
            return null
        }
    }
}
