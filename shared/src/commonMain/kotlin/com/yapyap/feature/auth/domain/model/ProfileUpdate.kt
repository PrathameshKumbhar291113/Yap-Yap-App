package com.yapyap.feature.auth.domain.model

data class ProfileUpdate(
    val name: String? = null,
    val dob: String? = null,
    val pincode: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
)
