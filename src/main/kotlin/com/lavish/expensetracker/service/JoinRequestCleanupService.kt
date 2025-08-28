package com.lavish.expensetracker.service

import com.lavish.expensetracker.model.JoinRequest
import com.lavish.expensetracker.model.JoinRequestStatus
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.JoinRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Periodic cleanup job that expires stale pending join requests older than JOIN_REQUEST_TTL_MS.
 * Strategy: mark them as CANCELLED (soft-expire) and remove requesterId from family's pending list.
 */
@Component
class JoinRequestCleanupService(
    private val joinRequestRepository: JoinRequestRepository,
    private val familyRepository: FamilyRepository
) {
    private val logger = LoggerFactory.getLogger(JoinRequestCleanupService::class.java)

    // Run hourly with initial delay 5 minutes after startup
    @Scheduled(initialDelay = 5 * 60 * 1000L, fixedDelay = 60 * 60 * 1000L)
    fun cleanExpiredPendingJoinRequests() {
        val cutoff = System.currentTimeMillis() - FamilyApplicationService.JOIN_REQUEST_TTL_MS
        return try {
            val expired = joinRequestRepository.findByStatusAndCreatedAtLessThan(JoinRequestStatus.PENDING, cutoff)
            if (expired.isEmpty()) return
            var familiesTouched = 0
            expired.forEach { req ->
                expireSingle(req)?.let { familiesTouched++ }
            }
            logger.info("JoinRequestCleanup: expired=${expired.size} familiesTouched=$familiesTouched cutoff=$cutoff")
        } catch (ex: Exception) {
            logger.error("JoinRequestCleanup failed: ${ex.message}", ex)
        }
    }

    private fun expireSingle(pending: JoinRequest): Boolean {
        val familyOpt = try { familyRepository.findById(pending.familyId) } catch (_: Exception) { return false }
        if (familyOpt.isEmpty) return false
        val family = familyOpt.get()
        val inList = family.pendingJoinRequests.any { it.userId == pending.requesterId }
        val updatedReq = pending.copy(status = JoinRequestStatus.CANCELLED, updatedAt = System.currentTimeMillis())
        try {
            joinRequestRepository.save(updatedReq)
        } catch (ex: Exception) {
            logger.warn("Failed to update expired join request ${pending.id}: ${ex.message}")
        }
        if (!inList) return false
        val newFamily = family.copy(
            pendingJoinRequests = family.pendingJoinRequests.filterNot { it.userId == pending.requesterId }.toMutableList(),
            updatedAt = System.currentTimeMillis()
        )
        return try {
            familyRepository.save(newFamily)
            true
        } catch (ex: Exception) {
            logger.warn("Failed to update family pending list for expired request ${pending.id}: ${ex.message}")
            false
        }
    }
}
