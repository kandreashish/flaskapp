package com.example.expensetracker.model

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "refresh_tokens")
data class RefreshToken(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "token", unique = true, nullable = false)
    val token: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),

    @Column(name = "is_revoked", nullable = false)
    var isRevoked: Boolean = false
)
