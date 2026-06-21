package com.yapyap.core.datastore

interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveCachedUser(json: String?)
    fun getCachedUser(): String?
    fun saveDeviceToken(token: String?)
    fun getDeviceToken(): String?
    fun setPushPermissionDenied(denied: Boolean)
    fun isPushPermissionDenied(): Boolean
    fun clear()
}

expect fun createTokenStorage(): TokenStorage
