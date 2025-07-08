package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/notifications")
class NotificationController @Autowired constructor(
    private val notificationRepository: NotificationRepository,
    private val authUtil: AuthUtil
) {
    @GetMapping
    fun getAllNotifications(): ResponseEntity<List<Notification>> {
        val userId = authUtil.getCurrentUserId()
        val notifications = notificationRepository.findAllBySenderId(userId)
        return ResponseEntity.ok(notifications)
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
