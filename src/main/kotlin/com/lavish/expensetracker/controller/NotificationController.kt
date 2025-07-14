package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.PagedResponse
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/notifications")
class NotificationController @Autowired constructor(
    private val notificationRepository: NotificationRepository,
    private val familyRepository: FamilyRepository,
    private val authUtil: AuthUtil
) {
    companion object {
        private const val DEFAULT_SIZE = 10
        private const val MAX_SIZE = 100
    }

    @GetMapping
    fun getAllNotifications(
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastNotificationId: String?
    ): PagedResponse<Notification> {
        val userId = authUtil.getCurrentUserId()

        // Get user's family - notifications are family-based
        val family = familyRepository.findByMembersIdsContains(userId)
            ?: familyRepository.findByHeadId(userId)
            ?: throw RuntimeException("User is not a member of any family")

        val familyId = family.familyId

        // Validate size parameter
        val validatedSize = when {
            size <= 0 -> DEFAULT_SIZE
            size > MAX_SIZE -> MAX_SIZE
            else -> size
        }

        val pageable = PageRequest.of(0, validatedSize)

        val result = if (lastNotificationId != null) {
            // Cursor-based pagination: get notifications after the cursor
            val cursorNotification = notificationRepository.findById(lastNotificationId)
                .orElseThrow { RuntimeException("Cursor notification not found with id: $lastNotificationId") }

            notificationRepository.findByFamilyIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                familyId, cursorNotification.createdAt, pageable
            )
        } else {
            // First page: get latest notifications for the user's family
            notificationRepository.findByFamilyIdOrderByCreatedAtDesc(familyId, pageable)
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
            lastExpenseId = if (result.content.isNotEmpty()) result.content.last().id else null
        )
    }

    @GetMapping("/{id}")
    fun getNotificationById(@PathVariable id: String): ResponseEntity<Notification> {
        val notification = notificationRepository.findById(id)
        return if (notification.isPresent) ResponseEntity.ok(notification.get())
        else ResponseEntity.notFound().build()
    }

    @PostMapping
    fun createNotification(@RequestBody notification: Notification): ResponseEntity<Notification> {
        val saved = notificationRepository.save(notification.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis()))
        return ResponseEntity.ok(saved)
    }

    @PutMapping("/{id}")
    fun updateNotification(@PathVariable id: String, @RequestBody notification: Notification): ResponseEntity<Notification> {
        if (!notificationRepository.existsById(id)) return ResponseEntity.notFound().build()
        val updated = notificationRepository.save(notification.copy(id = id, createdAt = System.currentTimeMillis()))
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{id}")
    fun deleteNotification(@PathVariable id: String): ResponseEntity<Void> {
        if (!notificationRepository.existsById(id)) return ResponseEntity.notFound().build()
        notificationRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{id}/read")
    fun markNotificationAsRead(@PathVariable id: String): ResponseEntity<Notification> {
        val notificationOpt = notificationRepository.findById(id)
        if (!notificationOpt.isPresent) return ResponseEntity.notFound().build()
        val notification = notificationOpt.get().copy(read = true, createdAt = System.currentTimeMillis())
        val updated = notificationRepository.save(notification)
        return ResponseEntity.ok(updated)
    }
}
