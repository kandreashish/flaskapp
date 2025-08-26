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
    val firebaseUid: String,

    @Column(name = "family_id")
    val familyId: String? = null,

    @Column(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @Column(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @Column(name = "fcm_token")
    val fcmToken: String? = null,

    @Column(name = "profile_pic", nullable = true, length = 1000)
    val profilePic: String? = null,

    // Low resolution / thumbnail version of profile picture
    @Column(name = "profile_pic_low", nullable = true, length = 1000)
    val profilePicLow: String? = null,

    @Column(name = "currencyPreference", nullable = false)
    val currencyPreference: String = "₹",

    @ElementCollection
    @Column(name = "sent_join_requests")
    val sentJoinRequests: MutableList<String> = mutableListOf(),

    @Column(name = "onboarding_completed", nullable = false)
    val onboardingCompleted: Boolean = false
)
