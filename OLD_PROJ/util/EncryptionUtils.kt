package com.oklookat.spectra.util

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    private fun deriveKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray(Charsets.UTF_8)
        val keyBytes = digest.digest(bytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(data: String, key: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val secretKey = deriveKey(key)
        // Используем фиксированный IV для простоты (в P2P с одноразовым токеном это допустимо)
        // или можно передавать IV вместе с данными.
        val iv = IvParameterSpec(ByteArray(16)) 
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(encryptedData: String, key: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val secretKey = deriveKey(key)
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }
}
