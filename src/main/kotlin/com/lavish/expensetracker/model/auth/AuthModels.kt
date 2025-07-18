package com.lavish.expensetracker.model.auth

import com.lavish.expensetracker.model.ExpenseUser
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@kotlinx.serialization.Serializable
data class LoginRequest(
    @field:Email(message = "Email should be valid")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String
)

@kotlinx.serialization.Serializable
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

data class FirebaseLoginRequest(
    @field:NotBlank(message = "Firebase ID token is required")
    val idToken: String
)

data class SuccessAuthResponse(
    override val success: Boolean,
    override val message: String,
    val user: ExpenseUser? = null,
    val token: String? = null,
    val refreshToken: String? = null
) : AuthResponseBase

data class FailureAuthResponse(
    override val success: Boolean,
    override val message: String,
) : AuthResponseBase

interface AuthResponseBase {
    val success: Boolean
    val message: String
}

data class FirebaseUserInfo(
    val uid: String,
    val name: String?,
    val email: String?,
    val picture: String?
)
