package com.forge.bridge.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores secrets (API keys, OAuth tokens, refresh tokens, session cookies) in
 * an EncryptedSharedPreferences file backed by Android Keystore.
 */
@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            VAULT_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun storeApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString(keyApi(providerId), apiKey).apply()
    }

    fun getApiKey(providerId: String): String? =
        prefs.getString(keyApi(providerId), null)

    /**
     * Store a token of a given kind. Common kinds: "SESSION", "ACCESS",
     * "REFRESH", "COOKIE". Kept as separate slots so refresh flows can replace
     * just the access token without losing the refresh token.
     */
    fun storeToken(providerId: String, kind: String, token: String) {
        prefs.edit().putString(keyToken(providerId, kind), token).apply()
    }

    fun getToken(providerId: String, kind: String): String? =
        prefs.getString(keyToken(providerId, kind), null)

    fun storeTokenExpiry(providerId: String, kind: String, expiresAtMillis: Long) {
        prefs.edit().putLong(keyExpiry(providerId, kind), expiresAtMillis).apply()
    }

    fun getTokenExpiry(providerId: String, kind: String): Long =
        prefs.getLong(keyExpiry(providerId, kind), 0L)

    fun clearProvider(providerId: String) {
        val edit = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("$providerId:") }
            .forEach { edit.remove(it) }
        edit.apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun keyApi(p: String) = "$p:api_key"
    private fun keyToken(p: String, k: String) = "$p:token:$k"
    private fun keyExpiry(p: String, k: String) = "$p:expiry:$k"

    companion object {
        private const val VAULT_FILE = "forge_bridge_vault"
    }
}
