package com.example.data

import android.util.Base64

object SecurityUtils {
    // Secret salt to obfuscate passwords and server URLs in the database
    private const val SECRET_KEY_OBFUSCATION = "SMARTER_IPTV_XOR_SALT_KEY_2026"

    /**
     * Encrypts/obfuscates a plain text string using Base64 and XOR salt
     */
    fun encrypt(plainText: String?): String {
        if (plainText.isNullOrEmpty()) return ""
        try {
            val bytes = plainText.toByteArray(Charsets.UTF_8)
            val keyBytes = SECRET_KEY_OBFUSCATION.toByteArray(Charsets.UTF_8)
            val encryptedBytes = ByteArray(bytes.size)
            for (i in bytes.indices) {
                encryptedBytes[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return plainText
        }
    }

    /**
     * Decrypts/de-obfuscates an encrypted string
     */
    fun decrypt(encryptedText: String?): String {
        if (encryptedText.isNullOrEmpty()) return ""
        try {
            val bytes = Base64.decode(encryptedText, Base64.NO_WRAP)
            val keyBytes = SECRET_KEY_OBFUSCATION.toByteArray(Charsets.UTF_8)
            val decryptedBytes = ByteArray(bytes.size)
            for (i in bytes.indices) {
                decryptedBytes[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return encryptedText
        }
    }
}
