package com.yapyap.feature.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val name: String,
    val phone: String,
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
)

@Serializable
data class AuthTokensDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class RefreshTokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class ProfileResponseDto(
    val user: UserDto,
)

@Serializable
data class UpdateProfileRequestDto(
    val name: String? = null,
    val dob: String? = null,
    val pincode: String? = null,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class UserDto(
    val id: Int,
    val uid: String,
    val email: String,
    val phone: String? = null,
    val name: String? = null,
    val dob: String? = null,
    val pincode: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
