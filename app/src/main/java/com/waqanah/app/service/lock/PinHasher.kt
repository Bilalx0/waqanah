package com.waqanah.app.service.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {

    private const val SALT_SIZE_BYTES = 16
    private const val KEY_LENGTH_BITS = 256
    private const val DEFAULT_ITERATIONS = 120_000

    fun newSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hash(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    fun verify(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = DEFAULT_ITERATIONS): Boolean {
        val computed = hash(pin, salt, iterations)
        return MessageDigest.isEqual(computed, expected)
    }

    fun toBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun fromBase64(str: String): ByteArray {
        return Base64.getDecoder().decode(str)
    }
}
