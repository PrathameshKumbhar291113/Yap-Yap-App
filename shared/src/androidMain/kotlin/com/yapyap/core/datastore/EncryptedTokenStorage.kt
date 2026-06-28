package com.yapyap.core.datastore

import android.content.Context
import android.content.SharedPreferences
import android.security.KeyStoreException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.touchlab.kermit.Logger
import com.yapyap.core.util.StorageKeys
import org.koin.mp.KoinPlatformTools
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class EncryptedTokenStorage(
    context: Context,
) : TokenStorage {
    private val prefs: SharedPreferences = openEncryptedPrefs(context.applicationContext)

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(StorageKeys.ACCESS_TOKEN, accessToken)
            .putString(StorageKeys.REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun getAccessToken(): String? = prefs.getString(StorageKeys.ACCESS_TOKEN, null)

    override fun getRefreshToken(): String? = prefs.getString(StorageKeys.REFRESH_TOKEN, null)

    override fun saveCachedUser(json: String?) {
        prefs.edit().putString(StorageKeys.CACHED_USER, json).apply()
    }

    override fun getCachedUser(): String? = prefs.getString(StorageKeys.CACHED_USER, null)

    override fun saveDeviceToken(token: String?) {
        prefs.edit().putString(StorageKeys.DEVICE_TOKEN, token).apply()
    }

    override fun getDeviceToken(): String? = prefs.getString(StorageKeys.DEVICE_TOKEN, null)

    override fun setPushPermissionDenied(denied: Boolean) {
        prefs.edit().putBoolean(StorageKeys.PUSH_PERMISSION_DENIED, denied).apply()
    }

    override fun isPushPermissionDenied(): Boolean =
        prefs.getBoolean(StorageKeys.PUSH_PERMISSION_DENIED, false)

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        private val log = Logger.withTag("TokenStorage")

        private fun openEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                createEncryptedPrefs(context)
            } catch (error: Exception) {
                if (!isRecoverableEncryptionFailure(error)) throw error
                log.w(error) {
                    "Encrypted prefs unreadable — clearing stale data and recreating " +
                        "(user will need to sign in again)"
                }
                context.deleteSharedPreferences(StorageKeys.PREFS_FILE_NAME)
                createEncryptedPrefs(context)
            }
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                StorageKeys.PREFS_FILE_NAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        private fun isRecoverableEncryptionFailure(error: Throwable): Boolean {
            var cause: Throwable? = error
            while (cause != null) {
                when (cause) {
                    is AEADBadTagException,
                    is KeyStoreException,
                    is GeneralSecurityException,
                    -> return true
                }
                cause = cause.cause
            }
            return false
        }
    }
}

actual fun createTokenStorage(): TokenStorage {
    val koin = KoinPlatformTools.defaultContext().getOrNull()
        ?: error("Koin must be started before creating TokenStorage")
    val context = koin.get<Context>()
    return EncryptedTokenStorage(context)
}

internal actual class SecureKeyValueStore actual constructor(service: String) {
    actual fun putString(key: String, value: String?) = Unit
    actual fun getString(key: String): String? = null
    actual fun remove(key: String) = Unit
    actual fun clear() = Unit
}
