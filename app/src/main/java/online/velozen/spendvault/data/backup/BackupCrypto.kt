package com.spendlens.app.data.backup

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based AES-256-GCM sealing for local backups (issue #13). Pure JCE — no Android
 * dependencies — so it is unit-testable on the JVM.
 *
 * File layout: [MAGIC(4) | version(1) | salt(16) | iv(12) | GCM ciphertext+tag]. The key is derived
 * with PBKDF2-HMAC-SHA256 over the password and a per-file random salt, so the same password yields
 * a different blob each time and a wrong password fails the GCM tag check ([BadPasswordException])
 * rather than producing garbage.
 */
object BackupCrypto {

    class BadPasswordException : Exception("Wrong password or corrupt backup file")

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + byteArrayOf(VERSION.toByte()) + salt + iv + ciphertext
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        val headerLen = MAGIC.size + 1 + SALT_BYTES + IV_BYTES
        require(blob.size > headerLen) { "Backup file is too small" }
        require(MAGIC.indices.all { blob[it] == MAGIC[it] }) { "Not a SpendLens backup file" }
        var off = MAGIC.size + 1 // skip magic + version byte
        val salt = blob.copyOfRange(off, off + SALT_BYTES); off += SALT_BYTES
        val iv = blob.copyOfRange(off, off + IV_BYTES); off += IV_BYTES
        val ciphertext = blob.copyOfRange(off, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw BadPasswordException()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    val MAGIC = "SLB1".toByteArray(Charsets.US_ASCII)
    const val VERSION = 1
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
}
