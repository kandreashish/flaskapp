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
        private const val MAX_TOTAL_ATTEMPTS_PER_FAMILY = 2 // initial + 1 resend
    }

    fun sendJoinRequest(requesterId: String, familyId: String, message: String? = null): JoinRequest {
        val family = familyRepository.findById(familyId).orElse(null)
            ?: throw IllegalArgumentException("Family not found")
        if (family.membersIds.contains(requesterId)) {
            throw IllegalStateException("User is already a member of this family")
        }
        // Simple cap
        val attempts = joinRequestRepository.findByRequesterIdAndFamilyIdOrderByCreatedAtDesc(requesterId, familyId)
        if (attempts.size >= MAX_TOTAL_ATTEMPTS_PER_FAMILY) {
            throw IllegalStateException("Max retries over. Ask family owner to send")
        }
        // Cancel existing pending (keep only new one logically active)
        attempts.filter { it.status == JoinRequestStatus.PENDING }.forEach { jr ->
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
        // Deprecated logic removed; retain method for any legacy calls but now just returns null.
        return null
    }
}
