package com.yapyap.core.datastore

internal expect class SecureKeyValueStore(service: String) {
    fun putString(key: String, value: String?)
    fun getString(key: String): String?
    fun remove(key: String)
    fun clear()
}
