package com.vcam.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM key held in the Android Keystore.
 *
 * Used to encrypt the on-device diagnostics log: it contains file paths and device camera
 * ids, so it is not left in plain text on disk. The key never leaves the TEE/StrongBox and
 * is not exportable.
 */
class KeystoreVault(private val alias: String = DEFAULT_ALIAS) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    fun decrypt(payload: ByteArray): ByteArray? {
        if (payload.size < 2) return null
        val ivSize = payload[0].toInt()
        if (payload.size < 1 + ivSize) return null
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val body = payload.copyOfRange(1 + ivSize, payload.size)
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(body)
        }.getOrNull()
    }

    fun deleteKey() {
        runCatching { keyStore.deleteEntry(alias) }
    }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        const val DEFAULT_ALIAS = "vcam_diagnostics_v1"
    }
}
