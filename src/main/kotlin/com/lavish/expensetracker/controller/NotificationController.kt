package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.NotificationType
import com.lavish.expensetracker.model.PagedResponse
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.AuthUtil
import org.slf4j.LoggerFactory
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
        private val logger = LoggerFactory.getLogger(NotificationController::class.java)
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
        val startTime = System.currentTimeMillis()
        logger.info("=== GET /api/notifications - Starting getAllNotifications ===")
        logger.info("Request parameters - size: {}, lastNotificationId: {}", size, lastNotificationId)

        try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Retrieved current user ID: {}", userId)

            // Validate size parameter
            val validatedSize = when {
                size <= 0 -> {
                    logger.warn("Invalid size parameter: {} (<=0), using default size: {}", size, DEFAULT_SIZE)
                    DEFAULT_SIZE
                }
                size > MAX_SIZE -> {
                    logger.warn("Size parameter: {} exceeds maximum allowed: {}, using max size", size, MAX_SIZE)
                    MAX_SIZE
                }
                else -> {
                    logger.debug("Using provided size parameter: {}", size)
                    size
                }
            }

            val pageable = PageRequest.of(0, validatedSize)
            logger.debug("Created PageRequest with page: 0, size: {}", validatedSize)

            val result = if (lastNotificationId != null) {
                logger.info("Performing cursor-based pagination with lastNotificationId: {}", lastNotificationId)
                try {
                    // Cursor-based pagination: get notifications after the cursor
                    val cursorNotification = notificationRepository.findById(lastNotificationId)
                        .orElseThrow {
                            logger.error("Cursor notification not found with id: {}", lastNotificationId)
                            RuntimeException("Cursor notification not found with id: $lastNotificationId")
                        }
                    logger.debug("Cursor notification found: {}", cursorNotification)

                    // Get notifications for the current user as receiver, ordered by timestamp descending
                    val notifications = notificationRepository.findByReceiverIdOrderByTimestampDesc(userId, pageable)
                    logger.info("Retrieved {} notifications for userId: {} with cursor pagination", notifications.totalElements, userId)
                    notifications
                } catch (e: Exception) {
                    logger.error("Error during cursor-based pagination for userId: {}, lastNotificationId: {}", userId, lastNotificationId, e)
                    throw e
                }
            } else {
                logger.info("Performing first page pagination for userId: {}", userId)
                try {
                    // First page: get latest notifications for the current user as receiver
                    val notifications = notificationRepository.findByReceiverIdOrderByTimestampDesc(userId, pageable)
                    logger.info("Retrieved {} notifications for userId: {} (first page)", notifications.totalElements, userId)
                    notifications
                } catch (e: Exception) {
                    logger.error("Error during first page pagination for userId: {}", userId, e)
                    throw e
                }
            }

            val response = PagedResponse(
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

            val executionTime = System.currentTimeMillis() - startTime
            logger.info("Successfully retrieved notifications - Content size: {}, Total elements: {}, Total pages: {}, Execution time: {}ms",
                response.content.size, response.totalElements, response.totalPages, executionTime)
            logger.info("=== GET /api/notifications - Completed successfully ===")

            return response
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== GET /api/notifications - Failed after {}ms ===", executionTime, e)
            throw e
        }
    }

    @GetMapping("/{id}")
    fun getNotificationById(@PathVariable id: Long): ResponseEntity<BasicResponse> {
        val startTime = System.currentTimeMillis()
        logger.info("=== GET /api/notifications/{} - Starting getNotificationById ===", id)

        try {
            logger.debug("Searching for notification with id: {}", id)
            val notification = notificationRepository.findById(id)

            return if (notification.isPresent) {
                logger.info("Notification found with id: {}", id)
                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== GET /api/notifications/{} - Completed successfully in {}ms ===", id, executionTime)
                ResponseEntity.ok(BasicResponse("Notification retrieved successfully", "success"))
            } else {
                logger.warn("Notification not found with id: {}", id)
                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== GET /api/notifications/{} - Completed with not found in {}ms ===", id, executionTime)
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== GET /api/notifications/{} - Failed after {}ms ===", id, executionTime, e)
            throw e
        }
    }

    @PostMapping
    fun createNotification(@RequestBody notification: Notification): ResponseEntity<BasicResponse> {
        val startTime = System.currentTimeMillis()
        logger.info("=== POST /api/notifications - Starting createNotification ===")
        logger.info("Incoming notification data - type: {}, title: {}, familyId: {}, senderId: {}, receiverId: {}",
            notification.type, notification.title, notification.familyId, notification.senderId, notification.receiverId)

        try {
            logger.debug("Validating notification data before save")
            if (notification.title.isBlank()) {
                logger.warn("Notification title is blank")
            }
            if (notification.message.isBlank()) {
                logger.warn("Notification message is blank")
            }

            val currentTimestamp = System.currentTimeMillis()
            logger.debug("Setting timestamp to: {}", currentTimestamp)

            val notificationToSave = notification.copy(timestamp = currentTimestamp)
            logger.debug("Saving notification to database")

            val saved = notificationRepository.save(notificationToSave)
            logger.info("Notification saved successfully with id: {}", saved.id)

            val executionTime = System.currentTimeMillis() - startTime
            logger.info("=== POST /api/notifications - Completed successfully in {}ms ===", executionTime)

            return ResponseEntity.ok(BasicResponse("Notification created successfully", "success"))
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== POST /api/notifications - Failed after {}ms ===", executionTime, e)
            throw e
        }
    }

    @PutMapping("/mark-all-read")
    fun markAllNotificationsAsRead(): ResponseEntity<BasicResponse> {
        val startTime = System.currentTimeMillis()
        logger.info("=== PUT /api/notifications/mark-all-read - Starting markAllNotificationsAsRead ===")

        try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Retrieved current user ID: {}", userId)
            logger.debug("Marking all notifications as read for userId: {}", userId)

            val updatedCount = notificationRepository.markAllAsReadByReceiverId(receiverId = userId)
            logger.info("Successfully marked {} notifications as read for userId: {}", updatedCount, userId)

            val executionTime = System.currentTimeMillis() - startTime
            logger.info("=== PUT /api/notifications/mark-all-read - Completed successfully in {}ms ===", executionTime)

            return ResponseEntity.ok(BasicResponse("$updatedCount notifications marked as read", "success"))
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== PUT /api/notifications/mark-all-read - Failed after {}ms ===", executionTime, e)
            throw e
        }
    }

    @PutMapping("/{id}")
    fun updateNotification(
        @PathVariable id: Long,
        @RequestBody notification: Notification
    ): ResponseEntity<BasicResponse> {
        val startTime = System.currentTimeMillis()
        logger.info("=== PUT /api/notifications/{} - Starting updateNotification ===", id)
        logger.info("Update request - id: {}, type: {}, title: {}, familyId: {}",
            id, notification.type, notification.title, notification.familyId)

        try {
            logger.debug("Checking if notification exists with id: {}", id)
            if (!notificationRepository.existsById(id)) {
                logger.warn("Notification not found with id: {} for update", id)
                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== PUT /api/notifications/{} - Completed with not found in {}ms ===", id, executionTime)
                return ResponseEntity.ok(BasicResponse("Notification not found", "error"))
            }

            logger.debug("Notification exists, proceeding with update")
            val currentTimestamp = System.currentTimeMillis()
            logger.debug("Setting update timestamp to: {}", currentTimestamp)

            val updated = notificationRepository.save(notification.copy(id = id, timestamp = currentTimestamp))
            logger.info("Notification updated successfully with id: {}", updated.id)

            val executionTime = System.currentTimeMillis() - startTime
            logger.info("=== PUT /api/notifications/{} - Completed successfully in {}ms ===", id, executionTime)

            return ResponseEntity.ok(BasicResponse("Notification updated successfully", "success", data = updated))
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== PUT /api/notifications/{} - Failed after {}ms ===", id, executionTime, e)
            throw e
        }
    }

    @DeleteMapping("/{id}")
    fun deleteNotification(@PathVariable id: Long): ResponseEntity<BasicResponse> {
        val startTime = System.currentTimeMillis()
        logger.info("=== DELETE /api/notifications/{} - Starting deleteNotification ===", id)

        try {
            logger.debug("Checking if notification exists with id: {}", id)
            if (!notificationRepository.existsById(id)) {
                logger.warn("Notification not found with id: {} for deletion", id)
                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== DELETE /api/notifications/{} - Completed with not found in {}ms ===", id, executionTime)
                return ResponseEntity.ok(BasicResponse("Notification not found", "error"))
            }

            logger.debug("Notification exists, proceeding with deletion")
            notificationRepository.deleteById(id)
            logger.info("Notification deleted successfully with id: {}", id)

            val executionTime = System.currentTimeMillis() - startTime
            logger.info("=== DELETE /api/notifications/{} - Completed successfully in {}ms ===", id, executionTime)

            return ResponseEntity.ok(BasicResponse("Notification deleted successfully", "success"))
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== DELETE /api/notifications/{} - Failed after {}ms ===", id, executionTime, e)
            throw e
        }
    }

    @PutMapping("/{id}/mark-read")
    fun markNotificationAsRead(@PathVariable id: Long): ResponseEntity<BasicResponse> {
        val startTime = System.currentTimeMillis()
        logger.info("=== PUT /api/notifications/{}/mark-read - Starting markNotificationAsRead ===", id)

        try {
            logger.debug("Searching for notification with id: {} to mark as read", id)
            val notification = notificationRepository.findById(id)

            return if (notification.isPresent) {
                val notif = notification.get()
                logger.info("Notification found with id: {}, current read status: {}", id, notif.isRead)

                if (notif.isRead) {
                    logger.debug("Notification with id: {} is already marked as read", id)
                } else {
                    logger.debug("Marking notification with id: {} as read", id)
                }

                val updated = notificationRepository.save(notif.copy(isRead = true))
                logger.info("Notification with id: {} successfully marked as read", updated.id)

                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== PUT /api/notifications/{}/mark-read - Completed successfully in {}ms ===", id, executionTime)

                ResponseEntity.ok(BasicResponse("Notification marked as read successfully", "success", data = updated))
            } else {
                logger.warn("Notification not found with id: {} for marking as read", id)
                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== PUT /api/notifications/{}/mark-read - Completed with not found in {}ms ===", id, executionTime)
                ResponseEntity.ok(BasicResponse("Notification not found", "error"))
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== PUT /api/notifications/{}/mark-read - Failed after {}ms ===", id, executionTime, e)
            throw e
        }
    }

    @GetMapping("/unread")
    fun getUnreadNotifications(): ResponseEntity<BasicResponse> {
        val startTime = System.currentTimeMillis()
        logger.info("=== GET /api/notifications/unread - Starting getUnreadNotifications ===")

        try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Retrieved current user ID: {}", userId)
            logger.debug("Searching for unread notifications for userId: {}", userId)

            val unreadNotifications = notificationRepository.findByReceiverIdAndIsReadFalseOrderByTimestampDesc(userId)
            val count = unreadNotifications.size

            logger.info("Found {} unread notifications for userId: {}", count, userId)
            logger.debug("Unread notification IDs: {}", unreadNotifications.map { it.id })

            val executionTime = System.currentTimeMillis() - startTime
            logger.info("=== GET /api/notifications/unread - Completed successfully in {}ms ===", executionTime)

            return ResponseEntity.ok(BasicResponse("Found $count unread notifications", "success"))
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== GET /api/notifications/unread - Failed after {}ms ===", executionTime, e)
            throw e
        }
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
        val startTime = System.currentTimeMillis()
        logger.info("=== GET /api/notifications/{}/details - Starting getNotificationDetails ===", id)

        try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Retrieved current user ID: {}", userId)
            logger.debug("Searching for notification with id: {} for details", id)

            val notification = notificationRepository.findById(id)

            return if (notification.isPresent) {
                val notif = notification.get()
                logger.info("Notification found with id: {}, type: {}, familyId: {}", id, notif.type, notif.familyId)

                // Security check: only allow users to see their own notifications
                if (notif.receiverId != userId) {
                    logger.warn("Access denied: User {} attempted to access notification {} belonging to user {}",
                        userId, id, notif.receiverId)
                    val executionTime = System.currentTimeMillis() - startTime
                    logger.info("=== GET /api/notifications/{}/details - Access denied after {}ms ===", id, executionTime)
                    return ResponseEntity.status(403).body(
                        BasicResponse("Access denied: You can only view your own notifications", "error")
                    )
                }

                logger.debug("Security check passed for notification id: {}", id)
                logger.debug("Generating custom message for notification type: {}", notif.type)

                val customMessage = generateCustomMessage(notif)
                val typeDescription = getTypeDescription(notif.type)

                logger.debug("Generated custom message: {}", customMessage)
                logger.debug("Generated type description: {}", typeDescription)

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

                logger.info("Successfully created notification detail response for id: {}", id)
                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== GET /api/notifications/{}/details - Completed successfully in {}ms ===", id, executionTime)

                ResponseEntity.ok(BasicResponse("Notification details retrieved successfully", "success", detailResponse))
            } else {
                logger.warn("Notification not found with id: {} for details", id)
                val executionTime = System.currentTimeMillis() - startTime
                logger.info("=== GET /api/notifications/{}/details - Completed with not found in {}ms ===", id, executionTime)
                ResponseEntity.ok(BasicResponse("Notification not found", "error"))
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("=== GET /api/notifications/{}/details - Failed after {}ms ===", id, executionTime, e)
            throw e
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
