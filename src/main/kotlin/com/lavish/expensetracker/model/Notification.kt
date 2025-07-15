package com.lavish.expensetracker.model

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id

enum class NotificationType(val type: String) {
    EXPENSE_ADDED("EXPENSE_ADDED"),
    EXPENSE_UPDATED("EXPENSE_UPDATED"),
    EXPENSE_DELETED("EXPENSE_DELETED"),
    JOIN_FAMILY_INVITATION("JOIN_FAMILY_INVITATION"),
    JOIN_FAMILY_REQUEST("JOIN_FAMILY_REQUEST"),
    FAMILY_MEMBER_JOINED("FAMILY_MEMBER_JOINED"),
    FAMILY_MEMBER_LEFT("FAMILY_MEMBER_LEFT"),
    FAMILY_MEMBER_REMOVED("FAMILY_MEMBER_REMOVED"),
    JOIN_FAMILY_INVITATION_REJECTED("JOIN_FAMILY_INVITATION_REJECTED"),
    JOIN_FAMILY_INVITATION_CANCELLED("JOIN_FAMILY_INVITATION_CANCELLED"),
    JOIN_FAMILY_REQUEST_REJECTED("JOIN_FAMILY_REQUEST_REJECTED"),
    JOIN_FAMILY_INVITATION_ACCEPTED("JOIN_FAMILY_INVITATION_ACCEPTED"),
    JOIN_FAMILY_REQUEST_ACCEPTED("JOIN_FAMILY_REQUEST_ACCEPTED"),
    BUDGET_LIMIT_REACHED("BUDGET_LIMIT_REACHED"),
    PAYMENT_REMINDER("PAYMENT_REMINDER"),
    OTHER("OTHER"),
    REMINDER("REMINDER"),
    GENERAL("GENERAL")
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
    val familyAliasName: String,
    val senderName: String,
    val senderId: String,
    val actionable: Boolean = false,
    val createdAt: Long,
    @Enumerated(EnumType.STRING)
    val type: NotificationType,
)
