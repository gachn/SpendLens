package com.spendlens.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Stores receipt images attached to transactions, **encrypted at rest** with an
 * Android Keystore-backed master key (same scheme as [com.spendlens.app.data.crypto.DatabaseKeyManager]).
 * Files live in app-private storage and are excluded from backup — they never leave the device.
 */
class ReceiptStore(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun encryptedFile(file: File) = EncryptedFile.Builder(
        context.applicationContext,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    /** Copies the picked image into encrypted app storage; returns the stored file's absolute path. */
    suspend fun save(txnId: Long, source: Uri): String = withContext(Dispatchers.IO) {
        val dest = File(dir, "receipt_${txnId}_${System.currentTimeMillis()}.enc")
        if (dest.exists()) dest.delete() // EncryptedFile refuses to overwrite
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: error("Could not read picked image")
        encryptedFile(dest).openFileOutput().use { it.write(bytes) }
        dest.absolutePath
    }

    /** Decrypts and decodes a stored receipt, or null if missing/unreadable. */
    suspend fun loadBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ByteArrayOutputStream().use { buffer ->
                encryptedFile(File(path)).openFileInput().use { it.copyTo(buffer) }
                buffer.toByteArray()
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    private companion object {
        const val DIR_NAME = "receipts"
    }
}
