package com.lavish.expensetracker.model

import jakarta.persistence.*
import kotlinx.serialization.Serializable

@Entity
@Table(name = "join_requests")
@Serializable
data class JoinRequest(
    @Id
    val id: String = "",

    @Column(name = "requester_id", nullable = false)
    val requesterId: String,

    @Column(name = "family_id", nullable = false)
    val familyId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: JoinRequestStatus = JoinRequestStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Long = System.currentTimeMillis(),

    @Column(name = "processed_by")
    val processedBy: String? = null,

    @Column(name = "message")
    val message: String? = null
)

enum class JoinRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
