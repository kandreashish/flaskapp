package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.NotificationType
import com.lavish.expensetracker.model.PagedResponse
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications")
class NotificationController @Autowired constructor(
    private val notificationRepository: NotificationRepository,
    private val authUtil: AuthUtil
) {
    companion object {
        private const val DEFAULT_SIZE = 10
        private const val MAX_SIZE = 100
    }

    data class BasicResponse(
        val message: String,
        val status: String,
        val data: Any? = null
    )

    @GetMapping
    fun getAllNotifications(
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastNotificationId: Long?
    ): PagedResponse<Notification> {
        val userId = authUtil.getCurrentUserId()

        // Validate size parameter
        val validatedSize = when {
            size <= 0 -> DEFAULT_SIZE
            size > MAX_SIZE -> MAX_SIZE
            else -> size
        }

        val pageable = PageRequest.of(0, validatedSize)

        val result = if (lastNotificationId != null) {
            // Cursor-based pagination: get notifications after the cursor
            // Note: For now, we'll just validate the cursor exists but use simple pagination
            notificationRepository.findById(lastNotificationId)
                .orElseThrow { RuntimeException("Cursor notification not found with id: $lastNotificationId") }

            // Get notifications for the current user as receiver, ordered by timestamp descending
            notificationRepository.findByReceiverIdOrderByTimestampDesc(userId, pageable)
        } else {
            // First page: get latest notifications for the current user as receiver
            notificationRepository.findByReceiverIdOrderByTimestampDesc(userId, pageable)
        }

        return PagedResponse(
            content = result.content,
            page = 0,
            size = validatedSize,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            isFirst = lastNotificationId == null,
            isLast = result.isLast,
            hasNext = result.hasNext(),
            hasPrevious = result.hasPrevious(),
            lastExpenseId = if (result.content.isNotEmpty()) result.content.last().id.toString() else null
        )
    }

    @GetMapping("/{id}")
    fun getNotificationById(@PathVariable id: Long): ResponseEntity<BasicResponse> {
        val notification = notificationRepository.findById(id)
        return if (notification.isPresent) {
            ResponseEntity.ok(BasicResponse("Notification retrieved successfully", "success"))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping
    fun createNotification(@RequestBody notification: Notification): ResponseEntity<BasicResponse> {
        val saved = notificationRepository.save(notification.copy(timestamp = System.currentTimeMillis()))
        return ResponseEntity.ok(BasicResponse("Notification created successfully", "success"))
    }

    @PutMapping("/mark-all-read")
    fun markAllNotificationsAsRead(): ResponseEntity<BasicResponse> {
        val userId = authUtil.getCurrentUserId()
        val updatedCount = notificationRepository.markAllAsReadByReceiverId(receiverId = userId)
        return ResponseEntity.ok(BasicResponse("$updatedCount notifications marked as read", "success"))
    }

    @PutMapping("/{id}")
    fun updateNotification(
        @PathVariable id: Long,
        @RequestBody notification: Notification
    ): ResponseEntity<BasicResponse> {
        if (!notificationRepository.existsById(id)) {
            return ResponseEntity.ok(BasicResponse("Notification not found", "error"))
        }
        val updated = notificationRepository.save(notification.copy(id = id, timestamp = System.currentTimeMillis()))
        return ResponseEntity.ok(BasicResponse("Notification updated successfully", "success", data = updated))
    }

    @DeleteMapping("/{id}")
    fun deleteNotification(@PathVariable id: Long): ResponseEntity<BasicResponse> {
        if (!notificationRepository.existsById(id)) {
            return ResponseEntity.ok(BasicResponse("Notification not found", "error"))
        }
        notificationRepository.deleteById(id)
        return ResponseEntity.ok(BasicResponse("Notification deleted successfully", "success"))
    }

    @PutMapping("/{id}/mark-read")
    fun markNotificationAsRead(@PathVariable id: Long): ResponseEntity<BasicResponse> {
        val notification = notificationRepository.findById(id)
        return if (notification.isPresent) {
            val updated = notificationRepository.save(notification.get().copy(isRead = true))
            ResponseEntity.ok(BasicResponse("Notification marked as read successfully", "success", data = updated))
        } else {
            ResponseEntity.ok(BasicResponse("Notification not found", "error"))
        }
    }

    @GetMapping("/unread")
    fun getUnreadNotifications(): ResponseEntity<BasicResponse> {
        val userId = authUtil.getCurrentUserId()
        val unreadNotifications = notificationRepository.findByReceiverIdAndIsReadFalseOrderByTimestampDesc(userId)
        val count = unreadNotifications.size
        return ResponseEntity.ok(BasicResponse("Found $count unread notifications", "success"))
    }

    data class NotificationDetailResponse(
        val id: Long,
        val title: String,
        val message: String,
        val customMessage: String,
        val timestamp: Long,
        val isRead: Boolean,
        val familyId: String,
        val familyAlias: String,
        val senderName: String,
        val senderId: String,
        val receiverId: String,
        val actionable: Boolean,
        val type: String,
        val typeDescription: String
    )

    @GetMapping("/{id}/details")
    fun getNotificationDetails(@PathVariable id: Long): ResponseEntity<BasicResponse> {
        val userId = authUtil.getCurrentUserId()
        val notification = notificationRepository.findById(id)

        return if (notification.isPresent) {
            val notif = notification.get()

            // Security check: only allow users to see their own notifications
            if (notif.receiverId != userId) {
                return ResponseEntity.status(403).body(
                    BasicResponse("Access denied: You can only view your own notifications", "error")
                )
            }

            val customMessage = generateCustomMessage(notif)
            val typeDescription = getTypeDescription(notif.type)

            val detailResponse = NotificationDetailResponse(
                id = notif.id,
                title = notif.title,
                message = notif.message,
                customMessage = customMessage,
                timestamp = notif.timestamp,
                isRead = notif.isRead,
                familyId = notif.familyId,
                familyAlias = notif.familyAlias,
                senderName = notif.senderName,
                senderId = notif.senderId,
                receiverId = notif.receiverId,
                actionable = notif.actionable,
                type = notif.type.name,
                typeDescription = typeDescription
            )

            ResponseEntity.ok(BasicResponse("Notification details retrieved successfully", "success", detailResponse))
        } else {
            ResponseEntity.ok(BasicResponse("Notification not found", "error"))
        }
    }

    private fun generateCustomMessage(notification: Notification): String {
        return when (notification.type) {
            NotificationType.EXPENSE_ADDED ->
                "💰 New expense added: ${notification.senderName} added an expense in ${notification.familyAlias} family"

            NotificationType.EXPENSE_UPDATED ->
                "✏️ Expense updated: ${notification.senderName} modified an expense in ${notification.familyAlias} family"

            NotificationType.EXPENSE_DELETED ->
                "🗑️ Expense deleted: ${notification.senderName} removed an expense from ${notification.familyAlias} family"

            NotificationType.JOIN_FAMILY_INVITATION ->
                "📨 Family invitation: You've been invited to join '${notification.familyAlias}' family by ${notification.senderName}"

            NotificationType.JOIN_FAMILY_REQUEST ->
                "🤝 Join request: ${notification.senderName} wants to join your '${notification.familyAlias}' family"

            NotificationType.FAMILY_MEMBER_JOINED ->
                "👋 New member: ${notification.senderName} has joined your '${notification.familyAlias}' family"

            NotificationType.FAMILY_MEMBER_LEFT ->
                "👋 Member left: ${notification.senderName} has left the '${notification.familyAlias}' family"

            NotificationType.FAMILY_MEMBER_REMOVED ->
                "❌ Removed from family: You have been removed from '${notification.familyAlias}' family by ${notification.senderName}"

            NotificationType.JOIN_FAMILY_INVITATION_REJECTED ->
                "❌ Invitation declined: ${notification.senderName} declined your invitation to join '${notification.familyAlias}' family"

            NotificationType.JOIN_FAMILY_INVITATION_CANCELLED ->
                "🚫 Invitation cancelled: Your invitation to join '${notification.familyAlias}' family has been cancelled"

            NotificationType.JOIN_FAMILY_REQUEST_REJECTED ->
                "❌ Request declined: Your request to join '${notification.familyAlias}' family was declined"

            NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED ->
                "✅ Invitation accepted: ${notification.senderName} accepted your invitation to join '${notification.familyAlias}' family"

            NotificationType.JOIN_FAMILY_REQUEST_ACCEPTED ->
                "✅ Request approved: Your request to join '${notification.familyAlias}' family has been approved"

            NotificationType.BUDGET_LIMIT_REACHED ->
                "⚠️ Budget alert: Budget limit reached for '${notification.familyAlias}' family"

            NotificationType.PAYMENT_REMINDER ->
                "💳 Payment reminder: You have pending payments in '${notification.familyAlias}' family"

            NotificationType.REMINDER ->
                "⏰ Reminder: ${notification.senderName} sent you a reminder"

            NotificationType.GENERAL ->
                "📢 General notification from ${notification.senderName}"

            NotificationType.OTHER ->
                "📝 ${notification.senderName}: ${notification.message}"
        }
    }

    private fun getTypeDescription(type: NotificationType): String {
        return when (type) {
            NotificationType.EXPENSE_ADDED -> "Expense Management"
            NotificationType.EXPENSE_UPDATED -> "Expense Management"
            NotificationType.EXPENSE_DELETED -> "Expense Management"
            NotificationType.JOIN_FAMILY_INVITATION -> "Family Invitation"
            NotificationType.JOIN_FAMILY_REQUEST -> "Family Request"
            NotificationType.FAMILY_MEMBER_JOINED -> "Family Activity"
            NotificationType.FAMILY_MEMBER_LEFT -> "Family Activity"
            NotificationType.FAMILY_MEMBER_REMOVED -> "Family Management"
            NotificationType.JOIN_FAMILY_INVITATION_REJECTED -> "Family Response"
            NotificationType.JOIN_FAMILY_INVITATION_CANCELLED -> "Family Management"
            NotificationType.JOIN_FAMILY_REQUEST_REJECTED -> "Family Response"
            NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED -> "Family Response"
            NotificationType.JOIN_FAMILY_REQUEST_ACCEPTED -> "Family Response"
            NotificationType.BUDGET_LIMIT_REACHED -> "Budget Alert"
            NotificationType.PAYMENT_REMINDER -> "Payment Alert"
            NotificationType.REMINDER -> "Personal Reminder"
            NotificationType.GENERAL -> "General Notice"
            NotificationType.OTHER -> "Other"
        }
    }
}
