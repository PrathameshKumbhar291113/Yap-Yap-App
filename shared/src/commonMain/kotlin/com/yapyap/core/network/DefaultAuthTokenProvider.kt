package com.yapyap.core.network

import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.realtime.TokenRefreshNotifier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

class DefaultAuthTokenProvider(
    private val tokenStorage: TokenStorage,
    private val refreshHttpClient: HttpClient,
    private val sessionEvents: SessionEventPublisher,
    private val tokenRefreshNotifier: TokenRefreshNotifier,
) : AuthTokenProvider {

    override fun getAccessToken(): String? = tokenStorage.getAccessToken()

    override fun getRefreshToken(): String? = tokenStorage.getRefreshToken()

    override fun saveTokens(accessToken: String, refreshToken: String) {
        tokenStorage.saveTokens(accessToken, refreshToken)
        tokenRefreshNotifier.notifyAccessTokenRefreshed(accessToken)
    }

    override fun clearTokens() {
        tokenStorage.clear()
    }

    override suspend fun refreshAccessToken(): Boolean {
        val refreshToken = getRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            if (!getAccessToken().isNullOrBlank()) {
                onSessionExpired()
            }
            return false
        }

        return when (
            val result = safeApiCall {
                val response = refreshHttpClient.post("/api/auth/refresh-token") {
                    setBody(RefreshTokenRequestDto(refreshToken))
                }
                val apiResponse = response.body<ApiResponse<RefreshTokenDataDto>>()
                if (!response.status.isSuccess()) {
                    throw ApiException.from(
                        httpStatus = response.status.value,
                        errorCode = apiResponse.errorCode,
                        message = apiResponse.message ?: "Token refresh failed",
                    )
                }
                apiResponse.requireData(response.status.value)
            }
        ) {
            is ApiResult.Success -> {
                saveTokens(result.data.accessToken, result.data.refreshToken)
                true
            }
            is ApiResult.Error -> {
                onSessionExpired()
                false
            }
        }
    }

    private fun onSessionExpired() {
        clearTokens()
        sessionEvents.emitSessionExpired()
    }
}
