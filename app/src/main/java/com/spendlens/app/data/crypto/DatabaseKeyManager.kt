package com.spendlens.app.data.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Owns the SQLCipher passphrase for the on-device database.
 *
 * The passphrase is a 32-byte CSPRNG value generated once and stored in
 * [EncryptedSharedPreferences], whose master key is held in the Android Keystore.
 * It is never logged and never leaves the device. See docs/DESIGN.md §7.
 */
class DatabaseKeyManager(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Returns the raw passphrase bytes for SQLCipher, generating one on first use. */
    fun getOrCreatePassphrase(): ByteArray {
        prefs.getString(KEY_PASSPHRASE, null)?.let { stored ->
            return Base64.decode(stored, Base64.NO_WRAP)
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP))
            .apply()
        return fresh
    }

    /** Wipes the stored passphrase (used by "delete all data"). */
    fun clear() {
        prefs.edit().remove(KEY_PASSPHRASE).apply()
    }

    private companion object {
        const val SECURE_PREFS = "spendlens_secure"
        const val KEY_PASSPHRASE = "db_passphrase"
    }
}
