package com.lavish.expensetracker.model

import jakarta.persistence.*
import kotlinx.serialization.Serializable

@Embeddable
@Serializable
data class PendingMemberInvite(
    val email: String,
    val userId: String? = null,
    val name: String? = null,
    val profilePic: String? = null,
    val profilePicLow: String? = null
)

@Embeddable
@Serializable
data class PendingJoinRequestRef(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val profilePic: String? = null,
    val profilePicLow: String? = null
)

@Entity
@Table(name = "families")
@Serializable
data class Family(
    @Id
    val familyId: String,

    @Column(nullable = false)
    val headId: String,

    @Column(nullable = false)
    val name: String,

    val aliasName: String,

    @Column(nullable = false)
    val maxSize: Int = 2,

    @ElementCollection
    val membersIds: MutableList<String> = mutableListOf(),

    @ElementCollection
    val pendingMemberEmails: MutableList<PendingMemberInvite> = mutableListOf(),

    @ElementCollection
    val pendingJoinRequests: MutableList<PendingJoinRequestRef> = mutableListOf(),

    @Column(nullable = false)
    val updatedAt: Long = System.currentTimeMillis(),

    @Column(nullable = true)
    var profilePhotoUrl: String? = null
)
