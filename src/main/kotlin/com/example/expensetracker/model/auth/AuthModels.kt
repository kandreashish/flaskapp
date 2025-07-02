package com.example.expensetracker.model.auth

import kotlinx.serialization.Serializable
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Serializable
data class LoginRequest(
    @field:Email(message = "Email should be valid")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String
)

@Serializable
data class SignupRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:Email(message = "Email should be valid")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String,

    val familyId: String = ""
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserInfo,
    val expiresIn: Long = 86400000 // 24 hours in milliseconds
)

@Serializable
data class UserInfo(
    val id: String,
    val name: String,
    val email: String,
    val familyId: String
)
