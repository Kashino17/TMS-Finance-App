package com.tms.banking.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Encrypted local storage for bank credentials.
 * Uses Android Keystore-backed encryption — credentials never leave the device unencrypted.
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "tms_secure_credentials",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Emirates NBD
    var enbdUsername: String
        get() = prefs.getString("enbd_username", "") ?: ""
        set(value) = prefs.edit().putString("enbd_username", value).apply()

    var enbdPassword: String
        get() = prefs.getString("enbd_password", "") ?: ""
        set(value) = prefs.edit().putString("enbd_password", value).apply()

    fun hasEnbdCredentials(): Boolean = enbdUsername.isNotBlank() && enbdPassword.isNotBlank()

    // Mashreq (for later)
    var mashreqUsername: String
        get() = prefs.getString("mashreq_username", "") ?: ""
        set(value) = prefs.edit().putString("mashreq_username", value).apply()

    var mashreqPassword: String
        get() = prefs.getString("mashreq_password", "") ?: ""
        set(value) = prefs.edit().putString("mashreq_password", value).apply()

    // FAB (for later)
    var fabUsername: String
        get() = prefs.getString("fab_username", "") ?: ""
        set(value) = prefs.edit().putString("fab_username", value).apply()

    var fabPassword: String
        get() = prefs.getString("fab_password", "") ?: ""
        set(value) = prefs.edit().putString("fab_password", value).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
