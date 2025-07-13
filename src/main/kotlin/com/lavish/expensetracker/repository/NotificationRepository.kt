package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : CrudRepository<Notification, String> {
    fun findAllBySenderId(senderId: String): List<Notification>
    fun findAllByFamilyId(familyId: String): List<Notification>
    fun findAllBySenderIdOrFamilyId(senderId: String, familyId: String): List<Notification>

    // Cursor-based pagination methods
    fun findBySenderIdAndCreatedAtLessThanOrderByCreatedAtDesc(senderId: String, createdAt: Long, pageable: Pageable): Page<Notification>
    fun findBySenderIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(senderId: String, createdAt: Long, pageable: Pageable): Page<Notification>
    fun findBySenderIdOrderByCreatedAtDesc(senderId: String, pageable: Pageable): Page<Notification>
}
