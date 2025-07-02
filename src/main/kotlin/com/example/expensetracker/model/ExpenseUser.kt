package com.example.expensetracker.model

import jakarta.persistence.*
import kotlinx.serialization.Serializable

@Entity
@Table(name = "expense_users")
@Serializable
data class ExpenseUser(
    @Id
    val id: String = "",

    @Column(nullable = false)
    val name: String? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(name = "firebase_uid", unique = true)
    var firebaseUid: String? = null,

    @Column(name = "family_id")
    val familyId: String? = null,

    @Column(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @Column(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis()
)
