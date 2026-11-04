package com.waqanah.app.service.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `correct PIN verifies and wrong PIN fails`() {
        val pin = "secret123"
        val wrongPin = "secret124"
        val salt = PinHasher.newSalt()

        // Use 1000 iterations in tests for speed
        val hash = PinHasher.hash(pin, salt, iterations = 1000)

        assertTrue(PinHasher.verify(pin, salt, hash, iterations = 1000))
        assertFalse(PinHasher.verify(wrongPin, salt, hash, iterations = 1000))
    }

    @Test
    fun `same PIN with different salts produces different hashes`() {
        val pin = "secret123"
        val salt1 = PinHasher.newSalt()
        val salt2 = PinHasher.newSalt()

        val hash1 = PinHasher.hash(pin, salt1, iterations = 1000)
        val hash2 = PinHasher.hash(pin, salt2, iterations = 1000)

        assertFalse(PinHasher.toBase64(hash1) == PinHasher.toBase64(hash2))
    }
}
