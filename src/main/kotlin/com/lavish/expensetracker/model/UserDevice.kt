package com.lavish.expensetracker.model

import jakarta.persistence.*
import kotlinx.serialization.Serializable

@Entity
@Table(name = "user_devices")
@Serializable
data class UserDevice(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = "",

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "fcm_token", nullable = false, unique = true)
    val fcmToken: String,

    @Column(name = "device_name")
    val deviceName: String? = null,

    @Column(name = "device_type")
    val deviceType: String? = null, // "android", "ios", "web"

    @Column(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @Column(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis(),

    @Column(name = "is_active")
    var isActive: Boolean = true
)
