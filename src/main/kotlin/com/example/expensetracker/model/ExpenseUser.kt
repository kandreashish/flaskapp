package com.example.expensetracker.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseUser(
    val id: String = "",
    val name: String? = "",
    val email: String? = "",
    @SerialName("profile_url")
    val profileUrl: String? = "",
    @SerialName("family_id")
    val familyId: String = "",
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    val password: String = "", // Added for authentication
    val roles: List<String> = listOf("USER") // Added for role-based access
)
