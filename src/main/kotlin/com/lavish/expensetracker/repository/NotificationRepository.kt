package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.Notification
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findAllBySenderId(senderId: String): List<Notification>
    fun findAllByFamilyId(familyId: String): List<Notification>
    fun findAllBySenderIdOrFamilyId(senderId: String, familyId: String): List<Notification>
    fun findByFamilyIdOrderByTimestampDesc(familyId: String): List<Notification>
    fun findByFamilyIdAndIsReadFalseOrderByTimestampDesc(familyId: String): List<Notification>

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.receiverId = :receiverId AND n.isRead = false")
    fun markAllAsReadByReceiverId(@Param("receiverId") receiverId: String): Int

    fun removeNotificationById(notificationId: Long)

    // Simple pagination methods
    fun findByFamilyIdOrderByTimestampDesc(familyId: String, pageable: Pageable): Page<Notification>
    fun findBySenderIdOrderByTimestampDesc(senderId: String, pageable: Pageable): Page<Notification>

    // Receiver-based methods for personalized notifications
    fun findByReceiverIdOrderByTimestampDesc(receiverId: String, pageable: Pageable): Page<Notification>
    fun findByReceiverIdAndIsReadFalseOrderByTimestampDesc(receiverId: String): List<Notification>
}
