package com.yapyap.core.datastore

import com.yapyap.core.util.StorageKeys

/**
 * iOS token storage backed by a Keychain stub ([SecureKeyValueStore]).
 * Replace [SecureKeyValueStore] with a Security-framework Keychain implementation for production.
 */
class KeychainTokenStorage : TokenStorage {
    private val secureStore = SecureKeyValueStore(StorageKeys.KEYCHAIN_SERVICE)

    override fun saveTokens(accessToken: String, refreshToken: String) {
        secureStore.putString(StorageKeys.ACCESS_TOKEN, accessToken)
        secureStore.putString(StorageKeys.REFRESH_TOKEN, refreshToken)
    }

    override fun getAccessToken(): String? = secureStore.getString(StorageKeys.ACCESS_TOKEN)

    override fun getRefreshToken(): String? = secureStore.getString(StorageKeys.REFRESH_TOKEN)

    override fun saveCachedUser(json: String?) {
        secureStore.putString(StorageKeys.CACHED_USER, json)
    }

    override fun getCachedUser(): String? = secureStore.getString(StorageKeys.CACHED_USER)

    override fun saveDeviceToken(token: String?) {
        secureStore.putString(StorageKeys.DEVICE_TOKEN, token)
    }

    override fun getDeviceToken(): String? = secureStore.getString(StorageKeys.DEVICE_TOKEN)

    override fun setPushPermissionDenied(denied: Boolean) {
        secureStore.putString(StorageKeys.PUSH_PERMISSION_DENIED, denied.toString())
    }

    override fun isPushPermissionDenied(): Boolean =
        secureStore.getString(StorageKeys.PUSH_PERMISSION_DENIED)?.toBooleanStrictOrNull() == true

    override fun clear() {
        secureStore.clear()
    }
}

actual fun createTokenStorage(): TokenStorage = KeychainTokenStorage()
