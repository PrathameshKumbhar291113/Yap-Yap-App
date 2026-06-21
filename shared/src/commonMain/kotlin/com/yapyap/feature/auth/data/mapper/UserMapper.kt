package com.yapyap.feature.auth.data.mapper

import com.yapyap.feature.auth.data.dto.UpdateProfileRequestDto
import com.yapyap.feature.auth.data.dto.UserDto
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.ProfileUpdate

fun UserDto.toDomain(): User = User(
    id = id,
    uid = uid,
    email = email,
    phone = phone,
    name = name,
    dob = dob,
    pincode = pincode,
    avatarUrl = avatarUrl,
    lastSeenAt = lastSeenAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun User.toDto(): UserDto = UserDto(
    id = id,
    uid = uid,
    email = email,
    phone = phone,
    name = name,
    dob = dob,
    pincode = pincode,
    avatarUrl = avatarUrl,
    lastSeenAt = lastSeenAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProfileUpdate.toDto(): UpdateProfileRequestDto = UpdateProfileRequestDto(
    name = name,
    dob = dob,
    pincode = pincode,
    phone = phone,
    avatarUrl = avatarUrl,
)
