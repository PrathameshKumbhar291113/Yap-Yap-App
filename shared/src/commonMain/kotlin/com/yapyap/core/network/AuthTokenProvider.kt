package com.yapyap.core.network

interface AuthTokenProvider {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clearTokens()
    suspend fun refreshAccessToken(): Boolean
}
