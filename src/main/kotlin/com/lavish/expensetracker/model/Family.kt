package com.lavish.expensetracker.model

import jakarta.persistence.*
import kotlinx.serialization.Serializable

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
    val pendingMemberEmails: MutableList<String> = mutableListOf(),

    @ElementCollection
    val pendingJoinRequests: MutableList<String> = mutableListOf(),

    @Column(nullable = false)
    val updatedAt: Long = System.currentTimeMillis(),

    @Column(nullable = true)
    var profilePhotoUrl: String? = null
)
