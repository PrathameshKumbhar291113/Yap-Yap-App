package com.yapyap.core.network

import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
)

@Serializable
data class RefreshTokenDataDto(
    val accessToken: String,
    val refreshToken: String,
)
