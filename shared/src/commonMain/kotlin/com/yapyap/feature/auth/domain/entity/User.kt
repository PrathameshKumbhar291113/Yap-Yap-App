package com.yapyap.feature.auth.domain.entity

data class User(
    val id: Int,
    val uid: String,
    val email: String,
    val phone: String? = null,
    val name: String? = null,
    val dob: String? = null,
    val pincode: String? = null,
    val avatarUrl: String? = null,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
