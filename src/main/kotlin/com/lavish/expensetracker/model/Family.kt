package com.lavish.expensetracker.model

import jakarta.persistence.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Embeddable
@Serializable
data class PendingMembersDetails(
    val email: String? = null,
    val userId: String? = null,
    val name: String? = null,
    val profilePic: String? = null,
    val profilePicLow: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Embeddable
@Serializable
data class MemberJoinRecord(
    val userId: String,
    val joinedAt: Long = System.currentTimeMillis()
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
    val memberJoins: MutableList<MemberJoinRecord> = mutableListOf(),

    // Invitations sent by head to potential members
    @ElementCollection
    @SerialName("pendingMemberEmails")
    val pendingMemberEmails: MutableList<PendingMembersDetails> = mutableListOf(),

    // Join requests created by users wanting to join
    @ElementCollection
    @SerialName("pendingJoinRequests")
    val pendingJoinRequests: MutableList<PendingMembersDetails> = mutableListOf(),

    @Column(nullable = false)
    val updatedAt: Long = System.currentTimeMillis(),

    @Column(nullable = true)
    var profilePhotoUrl: String? = null
)
