package com.lavish.expensetracker.model

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

enum class NotificationType {
    FAMILY_JOIN, FAMILY_INVITE, EXPENSE_ADDED, OTHER
}

@Entity
data class Notification(
    @Id
    val id: String,
    val title: String,
    val message: String,
    val time: Long = 0L,
    val read: Boolean = false,
    val familyId: String,
    val senderName: String,
    val senderId: String,
    val actionable: Boolean = false,
    val createdAt: Long,
    @Enumerated(EnumType.STRING)
    val type: NotificationType,
)
