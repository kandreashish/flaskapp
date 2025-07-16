package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.Notification
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
}
