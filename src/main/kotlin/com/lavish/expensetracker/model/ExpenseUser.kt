package com.lavish.expensetracker.model

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

    @Column(name = "alias_name", unique = true)
    val aliasName: String,

    @Column(name = "firebase_uid", unique = true)
    val firebaseUid: String? = null,

    @Column(name = "family_id")
    val familyId: String? = null,

    @Column(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @Column(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @Column(name = "fcm_token")
    val fcmToken: String? = null,

    @Column(name = "profile_pic", nullable = true)
    val profilePic: String? = null,

    @Column(name = "currencyPreference", nullable = false)
    val currencyPreference: String = "₹"
)
