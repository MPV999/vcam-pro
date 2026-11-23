package com.vcam.core.security

import java.security.MessageDigest

/** SHA-256 helpers for build artifacts and frame integrity checks. */
object Sha256 {

    private val HEX = "0123456789abcdef".toCharArray()

    fun of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val output = CharArray(digest.size * 2)
        var index = 0
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            output[index++] = HEX[value ushr 4]
            output[index++] = HEX[value and 0x0F]
        }
        return String(output)
    }

    fun ofFile(path: String): String? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            builder.append(HEX[(byte.toInt() and 0xFF) ushr 4])
            builder.append(HEX[byte.toInt() and 0x0F])
        }
        return builder.toString()
    }
}
