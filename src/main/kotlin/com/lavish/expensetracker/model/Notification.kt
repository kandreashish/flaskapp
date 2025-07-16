package com.lavish.expensetracker.model

import jakarta.persistence.*

enum class NotificationType {
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    JOIN_FAMILY_INVITATION,
    JOIN_FAMILY_REQUEST,
    FAMILY_MEMBER_JOINED,
    FAMILY_MEMBER_LEFT,
    FAMILY_MEMBER_REMOVED,
    JOIN_FAMILY_INVITATION_REJECTED,
    JOIN_FAMILY_INVITATION_CANCELLED,
    JOIN_FAMILY_REQUEST_REJECTED,
    JOIN_FAMILY_INVITATION_ACCEPTED,
    JOIN_FAMILY_REQUEST_ACCEPTED,
    BUDGET_LIMIT_REACHED,
    PAYMENT_REMINDER,
    OTHER,
    REMINDER,
    GENERAL
}

@Entity
@Table(name = "notifications")
data class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 255)
    val title: String ,

    @Column(nullable = false, length = 1000)
    val message: String ,

    @Column(nullable = false)
    val timestamp: Long = System.currentTimeMillis(),

    @Column(nullable = false)
    val isRead: Boolean = false,

    @Column(nullable = false, length = 50)
    val familyId: String ,

    @Column(nullable = false, length = 10)
    val familyAlias: String ,

    @Column(nullable = false, length = 100)
    val senderName: String ,

    @Column(nullable = false, length = 50)
    val senderId: String ,

    @Column(nullable = false, length = 50)
    val receiverId: String ,

    @Column(nullable = false)
    val actionable: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val type: NotificationType = NotificationType.GENERAL
)
