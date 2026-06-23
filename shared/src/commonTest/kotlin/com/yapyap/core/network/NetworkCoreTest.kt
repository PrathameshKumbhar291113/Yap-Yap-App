package com.yapyap.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiExceptionTest {

    @Test
    fun mapsKnownErrorCodes() {
        val exception = ApiException.from(
            httpStatus = 401,
            errorCode = ApiException.ERROR_AUTH_INVALID,
            message = "Invalid credentials",
        )
        assertIs<ApiException.AuthInvalid>(exception)
        assertEquals("Invalid credentials", exception.message)
    }

    @Test
    fun mapsHttpStatusWhenErrorCodeMissing() {
        val exception = ApiException.from(httpStatus = 404, message = "Missing")
        assertIs<ApiException.NotFound>(exception)
    }

    @Test
    fun mapsValidationErrorCode() {
        val exception = ApiException.from(
            httpStatus = 400,
            errorCode = ApiException.ERROR_VALIDATION,
            message = "Invalid email",
        )
        assertIs<ApiException.ValidationError>(exception)
    }
}

class AuthTokenProviderContractTest {

    private class FakeTokenStorage : com.yapyap.core.datastore.TokenStorage {
        private var access: String? = null
        private var refresh: String? = null

        override fun saveTokens(accessToken: String, refreshToken: String) {
            access = accessToken
            refresh = refreshToken
        }

        override fun getAccessToken(): String? = access
        override fun getRefreshToken(): String? = refresh
        override fun saveCachedUser(json: String?) = Unit
        override fun getCachedUser(): String? = null
        override fun saveDeviceToken(token: String?) = Unit
        override fun getDeviceToken(): String? = null
        override fun setPushPermissionDenied(denied: Boolean) = Unit
        override fun isPushPermissionDenied(): Boolean = false
        override fun clear() {
            access = null
            refresh = null
        }
    }

    @Test
    fun authTokenProviderReadsFromStorage() {
        val storage = FakeTokenStorage()
        storage.saveTokens("access-1", "refresh-1")

        val provider = object : AuthTokenProvider {
            override fun getAccessToken() = storage.getAccessToken()
            override fun getRefreshToken() = storage.getRefreshToken()
            override fun saveTokens(accessToken: String, refreshToken: String) =
                storage.saveTokens(accessToken, refreshToken)
            override fun clearTokens() = storage.clear()
            override suspend fun refreshAccessToken() = false
        }

        assertEquals("access-1", provider.getAccessToken())
        assertEquals("refresh-1", provider.getRefreshToken())
    }
}
