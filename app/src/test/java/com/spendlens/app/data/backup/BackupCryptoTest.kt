package com.spendlens.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the password-based backup sealing (issue #13). Pure JCE — runs on the JVM. */
class BackupCryptoTest {

    private val payload = """{"tables":{"transactions":[{"id":1,"amount":1234}]}}""".toByteArray()

    @Test
    fun `round-trips plaintext with the correct password`() {
        val blob = BackupCrypto.encrypt(payload, "correct horse".toCharArray())
        val out = BackupCrypto.decrypt(blob, "correct horse".toCharArray())
        assertArrayEquals(payload, out)
    }

    @Test
    fun `wrong password fails the GCM tag check`() {
        val blob = BackupCrypto.encrypt(payload, "right".toCharArray())
        assertThrows(BackupCrypto.BadPasswordException::class.java) {
            BackupCrypto.decrypt(blob, "wrong".toCharArray())
        }
    }

    @Test
    fun `every export uses a fresh salt and iv so ciphertext differs`() {
        val a = BackupCrypto.encrypt(payload, "pw".toCharArray())
        val b = BackupCrypto.encrypt(payload, "pw".toCharArray())
        assertFalse("ciphertexts must differ", a.contentEquals(b))
        // Both still decrypt back to the same plaintext.
        assertArrayEquals(payload, BackupCrypto.decrypt(a, "pw".toCharArray()))
        assertArrayEquals(payload, BackupCrypto.decrypt(b, "pw".toCharArray()))
    }

    @Test
    fun `blob carries the magic header`() {
        val blob = BackupCrypto.encrypt(payload, "pw".toCharArray())
        assertTrue(BackupCrypto.MAGIC.indices.all { blob[it] == BackupCrypto.MAGIC[it] })
        assertEquals(BackupCrypto.VERSION, blob[BackupCrypto.MAGIC.size].toInt())
    }

    @Test
    fun `a foreign file is rejected before any crypto`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt("not a spendlens backup".toByteArray(), "pw".toCharArray())
        }
    }
}
