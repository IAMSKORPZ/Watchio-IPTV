package com.watchioiptv.nativeapp.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSecretStore(context: Context) : SecretStore {
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "watchio_native_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun putSecret(key: String, value: String) = withContext(Dispatchers.IO) {
        preferences.edit().putString(key, value).apply()
    }

    override suspend fun getSecret(key: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(key, null)
    }

    override suspend fun removeSecret(key: String) = withContext(Dispatchers.IO) {
        preferences.edit().remove(key).apply()
    }
}
