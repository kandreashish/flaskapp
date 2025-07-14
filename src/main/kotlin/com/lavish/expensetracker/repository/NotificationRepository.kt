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

    // Cursor-based pagination methods for senderId (existing)
    fun findBySenderIdAndCreatedAtLessThanOrderByCreatedAtDesc(senderId: String, createdAt: Long, pageable: Pageable): Page<Notification>
    fun findBySenderIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(senderId: String, createdAt: Long, pageable: Pageable): Page<Notification>
    fun findBySenderIdOrderByCreatedAtDesc(senderId: String, pageable: Pageable): Page<Notification>

    // Cursor-based pagination methods for familyId (new - correct approach)
    fun findByFamilyIdAndCreatedAtLessThanOrderByCreatedAtDesc(familyId: String, createdAt: Long, pageable: Pageable): Page<Notification>
    fun findByFamilyIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(familyId: String, createdAt: Long, pageable: Pageable): Page<Notification>
    fun findByFamilyIdOrderByCreatedAtDesc(familyId: String, pageable: Pageable): Page<Notification>

    // Find notifications for multiple families (when user is in multiple families)
    fun findByFamilyIdInAndCreatedAtLessThanOrderByCreatedAtDesc(familyIds: List<String>, createdAt: Long, pageable: Pageable): Page<Notification>
    fun findByFamilyIdInOrderByCreatedAtDesc(familyIds: List<String>, pageable: Pageable): Page<Notification>
}
