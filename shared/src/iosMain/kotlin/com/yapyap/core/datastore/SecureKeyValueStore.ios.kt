package com.yapyap.core.datastore

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * Keychain stub — persists via NSUserDefaults until native Keychain bindings are added.
 */
internal actual class SecureKeyValueStore actual constructor(
    private val service: String,
) {
    private val settings = Settings()

    actual fun putString(key: String, value: String?) {
        val namespacedKey = namespaced(key)
        if (value == null) {
            settings.remove(namespacedKey)
        } else {
            settings[namespacedKey] = value
        }
    }

    actual fun getString(key: String): String? = settings.getStringOrNull(namespaced(key))

    actual fun remove(key: String) {
        settings.remove(namespaced(key))
    }

    actual fun clear() {
        listOf(
            com.yapyap.core.util.StorageKeys.ACCESS_TOKEN,
            com.yapyap.core.util.StorageKeys.REFRESH_TOKEN,
            com.yapyap.core.util.StorageKeys.CACHED_USER,
        ).forEach { remove(it) }
    }

    private fun namespaced(key: String): String = "$service.$key"
}
