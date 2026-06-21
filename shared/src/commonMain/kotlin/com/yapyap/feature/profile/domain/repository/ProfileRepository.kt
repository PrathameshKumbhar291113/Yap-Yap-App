package com.yapyap.feature.profile.domain.repository

import com.yapyap.feature.auth.domain.entity.User

interface ProfileRepository {
    suspend fun getCurrentUser(): Result<User?>
}
