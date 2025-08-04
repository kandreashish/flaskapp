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

    fun sendJoinRequest(requesterId: String, familyId: String, message: String? = null): JoinRequest {
        // Check if user already has a pending request for this family
        val existingRequest = joinRequestRepository.findByRequesterIdAndFamilyIdAndStatus(
            requesterId, familyId, JoinRequestStatus.PENDING
        )
        if (existingRequest != null) {
            throw IllegalStateException("User already has a pending join request for this family")
        }

        // Check if family exists
        val family = familyRepository.findById(familyId).orElse(null)
            ?: throw IllegalArgumentException("Family not found")

        // Check if user is already a member
        if (family.membersIds.contains(requesterId)) {
            throw IllegalStateException("User is already a member of this family")
        }

        val joinRequest = JoinRequest(
            id = UUID.randomUUID().toString(),
            requesterId = requesterId,
            familyId = familyId,
            message = message
        )

        return joinRequestRepository.save(joinRequest)
    }

    fun getSentJoinRequests(userId: String): List<JoinRequest> {
        return joinRequestRepository.findByRequesterId(userId)
    }

    fun getPendingSentJoinRequests(userId: String): List<JoinRequest> {
        return joinRequestRepository.findByRequesterIdAndStatus(userId, JoinRequestStatus.PENDING)
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
}
