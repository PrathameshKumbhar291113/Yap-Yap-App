package com.yapyap.feature.auth.data.remote

import com.yapyap.core.network.ApiException
import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.ApiResult
import com.yapyap.core.network.requireData
import com.yapyap.core.network.safeApiCall
import com.yapyap.feature.auth.data.dto.AuthTokensDto
import com.yapyap.feature.auth.data.dto.LoginRequestDto
import com.yapyap.feature.auth.data.dto.ProfileResponseDto
import com.yapyap.feature.auth.data.dto.RefreshTokenRequestDto
import com.yapyap.feature.auth.data.dto.RefreshTokenResponseDto
import com.yapyap.feature.auth.data.dto.RegisterRequestDto
import com.yapyap.feature.auth.data.dto.UpdateProfileRequestDto
import com.yapyap.feature.auth.data.dto.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

class AuthApi(
    private val httpClient: HttpClient,
) {
    suspend fun login(request: LoginRequestDto): ApiResult<AuthTokensDto> {
        return postJson("/api/auth/login", request)
    }

    suspend fun register(request: RegisterRequestDto): ApiResult<AuthTokensDto> {
        return postJson("/api/auth/register", request)
    }

    suspend fun refreshToken(request: RefreshTokenRequestDto): ApiResult<RefreshTokenResponseDto> {
        return postJson("/api/auth/refresh-token", request)
    }

    suspend fun logout(): ApiResult<Unit> {
        return safeApiCall {
            val response = httpClient.post("/api/auth/logout")
            val apiResponse = response.body<ApiResponse<Unit?>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
        }
    }

    suspend fun updateProfile(request: UpdateProfileRequestDto): ApiResult<UserDto> {
        return safeApiCall {
            val response = httpClient.patch("/api/auth/profile") {
                setBody(request)
            }
            val apiResponse = response.body<ApiResponse<ProfileResponseDto>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
            apiResponse.requireData(response.status.value).user
        }
    }

    private suspend inline fun <reified Body, reified Data> postJson(
        path: String,
        body: Body,
    ): ApiResult<Data> {
        return safeApiCall {
            val response = httpClient.post(path) { setBody(body) }
            val apiResponse = response.body<ApiResponse<Data>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
            apiResponse.requireData(response.status.value)
        }
    }

    private fun apiError(httpStatus: Int, apiResponse: ApiResponse<*>): ApiException {
        return ApiException.from(
            httpStatus = httpStatus,
            errorCode = apiResponse.errorCode,
            message = apiResponse.message ?: "Request failed",
        )
    }
}
