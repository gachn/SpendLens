package com.spendlens.app.data

import java.security.MessageDigest

/** SHA-256 content hash used for idempotent import and exact-duplicate suppression. */
object Hashing {
    fun contentHash(sender: String, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$sender|$body".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
