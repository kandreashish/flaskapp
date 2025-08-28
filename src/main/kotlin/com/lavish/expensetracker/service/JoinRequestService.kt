package com.lavish.expensetracker.service

import com.lavish.expensetracker.model.JoinRequest
import com.lavish.expensetracker.model.JoinRequestStatus
import com.lavish.expensetracker.repository.JoinRequestRepository
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.ExpenseUserRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class JoinRequestService(
    private val joinRequestRepository: JoinRequestRepository,
    private val familyRepository: FamilyRepository,
    private val expenseUserRepository: ExpenseUserRepository
) {

    companion object {
        private const val WEEK_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_ATTEMPTS_PER_WEEK = 3
        private const val MAX_TOTAL_ATTEMPTS_PER_FAMILY = 5
        private val BACKOFF_SCHEDULE_MS = listOf(0L, 6L * 3600_000, 12L * 3600_000, 24L * 3600_000)
    }

    fun sendJoinRequest(requesterId: String, familyId: String, message: String? = null): JoinRequest {
        // Validate family
        val family = familyRepository.findById(familyId).orElse(null)
            ?: throw IllegalArgumentException("Family not found")
        // Already member?
        if (family.membersIds.contains(requesterId)) {
            throw IllegalStateException("User is already a member of this family")
        }
        // Throttle / cap logic
        computeThrottle(requesterId, familyId)?.let { throttle ->
            val reason = throttle["reason"] as? String
            if (reason == "MAX_RETRIES") {
                throw IllegalStateException("Max retries over. Ask family owner to send invitation")
            } else {
                throw IllegalStateException(throttle["message"] as? String ?: "Request throttled")
            }
        }
        // Cancel any existing pending requests for this family so only one remains logically active
        val existingPending = joinRequestRepository
            .findByRequesterIdAndFamilyIdOrderByCreatedAtDesc(requesterId, familyId)
            .filter { it.status == JoinRequestStatus.PENDING }
        existingPending.forEach { jr ->
            joinRequestRepository.save(jr.copy(status = JoinRequestStatus.CANCELLED, updatedAt = System.currentTimeMillis()))
        }
        val now = System.currentTimeMillis()
        val joinRequest = JoinRequest(
            id = UUID.randomUUID().toString(),
            requesterId = requesterId,
            familyId = familyId,
            message = message,
            status = JoinRequestStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        return joinRequestRepository.save(joinRequest)
    }

    fun getSentJoinRequests(userId: String): List<JoinRequest> {
        return joinRequestRepository.findByRequesterId(userId)
    }

    fun getPendingSentJoinRequests(userId: String): List<JoinRequest> {
        val pending = joinRequestRepository.findByRequesterIdAndStatus(userId, JoinRequestStatus.PENDING)
        // Return only the latest pending per family
        return pending.groupBy { it.familyId }.values.map { group -> group.maxByOrNull { it.createdAt }!! }
    }

    fun getReceivedJoinRequests(familyId: String): List<JoinRequest> {
        return joinRequestRepository.findByFamilyIdAndStatus(familyId, JoinRequestStatus.PENDING)
    }

    fun cancelJoinRequest(requesterId: String, familyId: String): JoinRequest {
        val joinRequest = joinRequestRepository.findByRequesterIdAndFamilyIdAndStatus(
            requesterId, familyId, JoinRequestStatus.PENDING
        ) ?: throw IllegalArgumentException("No pending join request found")

        val updatedRequest = joinRequest.copy(
            status = JoinRequestStatus.CANCELLED,
            updatedAt = System.currentTimeMillis()
        )

        return joinRequestRepository.save(updatedRequest)
    }

    fun acceptJoinRequest(requestId: String, processedBy: String): JoinRequest {
        val joinRequest = joinRequestRepository.findById(requestId).orElse(null)
            ?: throw IllegalArgumentException("Join request not found")

        if (joinRequest.status != JoinRequestStatus.PENDING) {
            throw IllegalStateException("Join request is not pending")
        }

        // Add user to family
        val family = familyRepository.findById(joinRequest.familyId).orElse(null)
            ?: throw IllegalArgumentException("Family not found")

        if (family.membersIds.size >= family.maxSize) {
            throw IllegalStateException("Family is already at maximum capacity")
        }

        // Update family membership
        val updatedFamily = family.copy(
            membersIds = family.membersIds.apply { add(joinRequest.requesterId) },
            updatedAt = System.currentTimeMillis()
        )
        familyRepository.save(updatedFamily)

        // Update user's family
        val user = expenseUserRepository.findById(joinRequest.requesterId).orElse(null)
            ?: throw IllegalArgumentException("User not found")

        val updatedUser = user.copy(
            familyId = joinRequest.familyId,
            updatedAt = System.currentTimeMillis()
        )
        expenseUserRepository.save(updatedUser)

        // Update join request status
        val updatedRequest = joinRequest.copy(
            status = JoinRequestStatus.ACCEPTED,
            processedBy = processedBy,
            updatedAt = System.currentTimeMillis()
        )

        return joinRequestRepository.save(updatedRequest)
    }

    fun rejectJoinRequest(requestId: String, processedBy: String): JoinRequest {
        val joinRequest = joinRequestRepository.findById(requestId).orElse(null)
            ?: throw IllegalArgumentException("Join request not found")

        if (joinRequest.status != JoinRequestStatus.PENDING) {
            throw IllegalStateException("Join request is not pending")
        }

        val updatedRequest = joinRequest.copy(
            status = JoinRequestStatus.REJECTED,
            processedBy = processedBy,
            updatedAt = System.currentTimeMillis()
        )

        return joinRequestRepository.save(updatedRequest)
    }

    private fun computeThrottle(userId: String, familyId: String): Map<String, Any>? {
        val now = System.currentTimeMillis()
        val attempts = joinRequestRepository.findByRequesterIdAndFamilyIdOrderByCreatedAtDesc(userId, familyId)
        if (attempts.isEmpty()) return null
        if (attempts.size >= MAX_TOTAL_ATTEMPTS_PER_FAMILY) {
            return mapOf(
                "reason" to "MAX_RETRIES",
                "message" to "Max retries over. Ask family owner to send invitation",
                "attempts" to attempts.size,
                "maxAttempts" to MAX_TOTAL_ATTEMPTS_PER_FAMILY
            )
        }
        val windowAttempts = attempts.filter { now - it.createdAt <= WEEK_WINDOW_MS }
        if (windowAttempts.size >= MAX_ATTEMPTS_PER_WEEK) {
            val nextAllowedAt = windowAttempts.minByOrNull { it.createdAt }!!.createdAt + WEEK_WINDOW_MS
            return mapOf(
                "reason" to "WEEKLY_LIMIT",
                "message" to "Weekly join request limit reached",
                "nextAllowedAt" to nextAllowedAt,
                "attemptsInWindow" to windowAttempts.size
            )
        }
        val latest = attempts.first()
        val backoffIndex = (windowAttempts.size - 1).coerceAtLeast(0)
        val requiredDelay = BACKOFF_SCHEDULE_MS.getOrNull(backoffIndex) ?: BACKOFF_SCHEDULE_MS.last()
        val elapsed = now - latest.createdAt
        if (elapsed < requiredDelay) {
            return mapOf(
                "reason" to "BACKOFF",
                "message" to "Join request backoff active",
                "cooldownMs" to (requiredDelay - elapsed)
            )
        }
        return null
    }
}
