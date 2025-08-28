package com.lavish.expensetracker.service

import com.lavish.expensetracker.controller.dto.*
import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.repository.JoinRequestRepository
import com.lavish.expensetracker.util.ApiResponseUtil
import com.lavish.expensetracker.util.AuthUtil
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.*

@Service
class FamilyApplicationService(
    private val familyRepository: FamilyRepository,
    private val userRepository: ExpenseUserRepository,
    private val notificationRepository: NotificationRepository,
    private val authUtil: AuthUtil,
    private val familyNotificationService: FamilyNotificationService,
    private val joinRequestRepository: JoinRequestRepository,
) {
    private val logger = LoggerFactory.getLogger(FamilyApplicationService::class.java)

    companion object {
        private const val FAMILY_MAX_SIZE = 10
        private const val MAX_GENERATION_ATTEMPTS = 100
        const val JOIN_REQUEST_TTL_MS = 3L * 24 * 60 * 60 * 1000
        private const val ATTEMPT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val MAX_ATTEMPTS_PER_WINDOW = 2 // initial + 1 resend within window
    }

    /* ===================== Public Endpoint Facade Methods ===================== */

    fun createFamily(request: CreateFamilyRequest): ResponseEntity<*> {
        validateFamilyName(request.familyName)?.let { return ApiResponseUtil.badRequest(it) }
        val userId = authUtil.getCurrentUserId()
        val user = userRepository.findById(userId).orElse(null)
            ?: return ApiResponseUtil.notFound("User not found")
        if (user.familyId != null) return ApiResponseUtil.conflict("Already in a family")
        val alias = generateUniqueAliasName()
        val family = Family(
            familyId = UUID.randomUUID().toString(),
            headId = userId,
            name = request.familyName.trim(),
            aliasName = alias,
            maxSize = FAMILY_MAX_SIZE,
            membersIds = mutableListOf(userId),
            updatedAt = System.currentTimeMillis()
        )
        familyRepository.save(family)
        userRepository.save(user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis()))
        return ResponseEntity.ok(BasicFamilySuccessResponse("Family created successfully", mapOf("family" to family, "members" to listMembers(family))))
    }

    fun getFamilyDetails(): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val familyId = user.familyId ?: return ApiResponseUtil.badRequest("Not in a family")
        val family = familyRepository.findById(familyId).orElse(null)
            ?: return ApiResponseUtil.notFound("Family not found")
        return ResponseEntity.ok(mapOf("family" to family, "members" to listMembers(family)))
    }

    fun joinFamily(request: JoinFamilyRequest): ResponseEntity<*> {
        validateAliasName(request.aliasName)?.let { return ApiResponseUtil.badRequest(it) }
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        if (user.familyId != null) return ApiResponseUtil.conflict("Already in a family")
        val family = familyRepository.findByAliasName(request.aliasName) ?: return ApiResponseUtil.notFound("Family not found")
        if (family.membersIds.size >= family.maxSize) return ApiResponseUtil.conflict("Family full")
        val updated = family.copy(membersIds = (family.membersIds + user.id).toMutableList(), updatedAt = now())
        familyRepository.save(updated)
        userRepository.save(user.copy(familyId = family.familyId, updatedAt = now()))
        return ResponseEntity.ok(BasicFamilySuccessResponse("Joined family successfully", mapOf("family" to updated, "members" to listMembers(updated))))
    }

    fun requestToJoinFamily(request: JoinFamilyRequest): ResponseEntity<*> {
        validateAliasName(request.aliasName)?.let { return ApiResponseUtil.badRequest(it) }
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        if (user.familyId != null) return ApiResponseUtil.conflict("Already in a family")
        val family = familyRepository.findByAliasName(request.aliasName) ?: return ApiResponseUtil.notFound("Family not found")
        if (family.membersIds.size >= family.maxSize) return ApiResponseUtil.conflict("Family full")
        val throttle = computeJoinRequestThrottle(user.id, family.familyId)
        if (throttle != null) return ResponseEntity.status(409).body(throttle)
        val updatedFamily = if (!family.pendingJoinRequests.contains(user.id)) {
            family.copy(pendingJoinRequests = (family.pendingJoinRequests + user.id).toMutableList(), updatedAt = now())
                .also { familyRepository.save(it) }
        } else family
        val joinReq = JoinRequest(
            id = UUID.randomUUID().toString(),
            requesterId = user.id,
            familyId = updatedFamily.familyId,
            message = null,
            status = JoinRequestStatus.PENDING,
            createdAt = now(),
            updatedAt = now()
        )
        joinRequestRepository.save(joinReq)
        notifyHeadNewJoinRequest(user, updatedFamily, joinReq)
        return ResponseEntity.ok(mapOf("message" to "Join request sent successfully", "familyName" to updatedFamily.name, "familyAlias" to updatedFamily.aliasName, "status" to "pending", "requestId" to joinReq.id))
    }

    private fun notifyHeadNewJoinRequest(user: ExpenseUser, family: Family, joinReq: JoinRequest) {
        val head = userRepository.findById(family.headId).orElse(null) ?: return
        val notif = createNotification(
            title = "New Family Join Request",
            message = "${user.name ?: user.email} wants to join your family '${family.name}'",
            familyId = family.familyId,
            senderName = user.name ?: user.email,
            senderId = user.id,
            receiverId = head.id,
            type = NotificationType.JOIN_FAMILY_REQUEST,
            familyAliasName = family.aliasName,
            actionable = true
        )
        val saved = saveNotification(notif)
        sendDataPush(head.fcmToken, NotificationType.JOIN_FAMILY_REQUEST, "New Family Join Request", "${user.name ?: user.email} wants to join your family '${family.name}'", mapOf(
            "familyId" to family.familyId,
            "requesterId" to user.id,
            "requesterName" to (user.name ?: user.email),
            "familyName" to family.name,
            "familyAlias" to family.aliasName,
            "joinRequestId" to joinReq.id
        ), saved?.id)
    }

    fun getOwnPendingJoinRequests(): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val pending = joinRequestRepository.findByRequesterIdAndStatus(user.id, JoinRequestStatus.PENDING)
        // ensure only one (latest) per family
        val distinctLatest = pending.groupBy { it.familyId }.values.map { group -> group.maxByOrNull { it.createdAt }!! }
        val enriched = distinctLatest.mapNotNull { req ->
            val fam = familyRepository.findById(req.familyId).orElse(null) ?: return@mapNotNull null
            mapOf(
                "request" to req,
                "family" to mapOf(
                    "familyId" to fam.familyId,
                    "name" to fam.name,
                    "alias" to fam.aliasName,
                    "headId" to fam.headId,
                    "memberCount" to fam.membersIds.size,
                    "maxSize" to fam.maxSize
                )
            )
        }
        return ResponseEntity.ok(mapOf("pendingJoinRequests" to enriched))
    }

    fun cancelOwnJoinRequest(request: JoinRequestActionRequest): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = familyRepository.findByAliasName(request.aliasName) ?: return ApiResponseUtil.notFound("Family not found")
        val pending = joinRequestRepository.findByRequesterIdAndFamilyIdAndStatus(user.id, family.familyId, JoinRequestStatus.PENDING)
            ?: return ApiResponseUtil.notFound("No pending join request to cancel")
        val cancelled = pending.copy(status = JoinRequestStatus.CANCELLED, updatedAt = now())
        joinRequestRepository.save(cancelled)
        if (family.pendingJoinRequests.contains(user.id)) {
            val updatedFamily = family.copy(pendingJoinRequests = (family.pendingJoinRequests - user.id).toMutableList(), updatedAt = now())
            familyRepository.save(updatedFamily)
        }
        return ResponseEntity.ok(mapOf("message" to "Join request cancelled", "request" to cancelled))
    }

    fun resendJoinRequest(request: JoinRequestActionRequest): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        if (user.familyId != null) return ApiResponseUtil.conflict("Already in a family")
        val family = familyRepository.findByAliasName(request.aliasName) ?: return ApiResponseUtil.notFound("Family not found")
        if (family.membersIds.size >= family.maxSize) return ApiResponseUtil.conflict("Family full")
        if (family.membersIds.contains(user.id)) return ApiResponseUtil.conflict("Already a member")
        val throttle = computeJoinRequestThrottle(user.id, family.familyId)
        if (throttle != null) return ResponseEntity.status(409).body(throttle)
        // cancel any previous pending requests for same family
        val existing = joinRequestRepository.findByRequesterIdAndFamilyIdOrderByCreatedAtDesc(user.id, family.familyId).filter { it.status == JoinRequestStatus.PENDING }
        existing.forEach { prev -> joinRequestRepository.save(prev.copy(status = JoinRequestStatus.CANCELLED, updatedAt = now())) }
        val ensuredFamily = if (!family.pendingJoinRequests.contains(user.id)) {
            val updated = family.copy(pendingJoinRequests = (family.pendingJoinRequests + user.id).toMutableList(), updatedAt = now())
            familyRepository.save(updated)
            updated
        } else family
        val newReq = JoinRequest(
            id = UUID.randomUUID().toString(),
            requesterId = user.id,
            familyId = ensuredFamily.familyId,
            message = request.message,
            status = JoinRequestStatus.PENDING,
            createdAt = now(),
            updatedAt = now()
        )
        val savedReq = joinRequestRepository.save(newReq)
        notifyHeadNewJoinRequest(user, ensuredFamily, savedReq)
        return ResponseEntity.ok(mapOf("message" to "Join request sent", "requestId" to savedReq.id))
    }

    fun leaveFamily(): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val familyId = user.familyId ?: return ApiResponseUtil.badRequest("Not in a family")
        val family = familyRepository.findById(familyId).orElse(null) ?: return ApiResponseUtil.notFound("Family not found")
        if (!family.membersIds.contains(user.id)) return ApiResponseUtil.badRequest("Not a member of this family")
        val remaining = family.membersIds.toMutableList().apply { remove(user.id) }
        if (remaining.isEmpty()) {
            familyRepository.delete(family)
            userRepository.save(user.copy(familyId = null, updatedAt = now()))
            return ResponseEntity.ok(mapOf("message" to "Family deleted"))
        }
        val newHead = if (family.headId == user.id) remaining.first() else family.headId
        val updated = family.copy(membersIds = remaining, headId = newHead, updatedAt = now())
        familyRepository.save(updated)
        userRepository.save(user.copy(familyId = null, updatedAt = now()))
        return ResponseEntity.ok(mapOf("message" to "Left family successfully"))
    }

    fun inviteMember(request: InviteMemberRequest): ResponseEntity<*> {
        validateEmail(request.invitedMemberEmail)?.let { return ApiResponseUtil.badRequest(it) }
        val head = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val familyId = head.familyId ?: return ApiResponseUtil.badRequest("Not in a family")
        val family = familyRepository.findById(familyId).orElse(null) ?: return ApiResponseUtil.notFound("Family not found")
        if (family.headId != head.id) return ApiResponseUtil.forbidden("Only head can invite")
        val invited = userRepository.findAll().find { it.email == request.invitedMemberEmail } ?: return ApiResponseUtil.notFound("User not found")
        if (invited.familyId != null) return ApiResponseUtil.conflict("User already in a family")
        if (family.pendingMemberEmails.contains(request.invitedMemberEmail)) return ApiResponseUtil.conflict("Already invited")
        val updated = family.copy(pendingMemberEmails = (family.pendingMemberEmails + request.invitedMemberEmail).toMutableList(), updatedAt = now())
        familyRepository.save(updated)
        sendInvitationNotification(invited, updated, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Invitation sent to ${request.invitedMemberEmail} and is pending acceptance", mapOf("family" to updated, "members" to listMembers(updated))))
    }

    fun resendInvitation(request: InviteMemberRequest): ResponseEntity<*> {
        validateEmail(request.invitedMemberEmail)?.let { return ApiResponseUtil.badRequest(it) }
        val head = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = head.familyId?.let { familyRepository.findById(it).orElse(null) } ?: return ApiResponseUtil.badRequest("Not in a family")
        if (family.headId != head.id) return ApiResponseUtil.forbidden("Only head can resend")
        val invited = userRepository.findAll().find { it.email == request.invitedMemberEmail } ?: return ApiResponseUtil.notFound("User not found")
        if (invited.familyId != null) return ApiResponseUtil.conflict("User already in a family")
        if (!family.pendingMemberEmails.contains(request.invitedMemberEmail)) return ApiResponseUtil.conflict("No pending invitation. Send new")
        sendInvitationNotification(invited, family, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Invitation resent to ${request.invitedMemberEmail} successfully", mapOf("family" to family, "members" to listMembers(family))))
    }

    fun cancelInvitation(request: CancelInvitationRequest): ResponseEntity<*> {
        validateEmail(request.invitedMemberEmail)?.let { return ApiResponseUtil.badRequest(it) }
        val head = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = head.familyId?.let { familyRepository.findById(it).orElse(null) } ?: return ApiResponseUtil.badRequest("Not in a family")
        if (family.headId != head.id) return ApiResponseUtil.forbidden("Only head can cancel")
        if (!family.pendingMemberEmails.contains(request.invitedMemberEmail)) return ApiResponseUtil.notFound("No pending invitation")
        val updated = family.copy(pendingMemberEmails = (family.pendingMemberEmails - request.invitedMemberEmail).toMutableList(), updatedAt = now())
        familyRepository.save(updated)
        val invited = userRepository.findAll().find { it.email == request.invitedMemberEmail }
        if (invited != null) notifyInvitationCancelled(invited, updated, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Invitation cancelled successfully", mapOf("family" to updated, "members" to listMembers(updated))))
    }

    fun updateFamilyName(request: UpdateFamilyNameRequest): ResponseEntity<*> {
        validateFamilyName(request.familyName)?.let { return ApiResponseUtil.badRequest(it) }
        val head = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = head.familyId?.let { familyRepository.findById(it).orElse(null) } ?: return ApiResponseUtil.badRequest("Not in a family")
        if (family.headId != head.id) return ApiResponseUtil.forbidden("Only head can update name")
        val conflict = familyRepository.findAll().any { it.name.equals(request.familyName.trim(), true) && it.familyId != family.familyId }
        if (conflict) return ApiResponseUtil.conflict("Family name already in use")
        val updated = family.copy(name = request.familyName.trim(), updatedAt = now())
        familyRepository.save(updated)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Family name updated successfully", mapOf("family" to updated, "members" to listMembers(updated))))
    }

    fun acceptInvitation(request: JoinFamilyRequest): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        if (user.familyId != null) return ApiResponseUtil.conflict("Already in a family")
        val family = familyRepository.findByAliasName(request.aliasName.trim()) ?: return ApiResponseUtil.notFound("Family not found")
        if (!family.pendingMemberEmails.contains(user.email)) return ApiResponseUtil.badRequest("No pending invitation")
        if (family.membersIds.size >= family.maxSize) return ApiResponseUtil.conflict("Family full")
        val updatedFamily = family.copy(
            membersIds = (family.membersIds + user.id).toMutableList(),
            pendingMemberEmails = (family.pendingMemberEmails - user.email).toMutableList(),
            updatedAt = now()
        )
        familyRepository.save(updatedFamily)
        userRepository.save(user.copy(familyId = updatedFamily.familyId, updatedAt = now()))
        val head = userRepository.findById(family.headId).orElse(null)
        if (head != null) notifyInvitationAccepted(user, updatedFamily, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Family invitation accepted successfully.", mapOf("family" to updatedFamily, "members" to listMembers(updatedFamily))))
    }

    fun rejectInvitation(request: RejectFamilyRequest): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = familyRepository.findByAliasName(request.aliasName.trim()) ?: return ApiResponseUtil.notFound("Family not found")
        val updatedFamily = family.copy(
            pendingMemberEmails = (family.pendingMemberEmails - user.email).toMutableList(),
            updatedAt = now()
        )
        familyRepository.save(updatedFamily)
        val head = userRepository.findById(family.headId).orElse(null)
        if (head != null) notifyInvitationRejected(user, updatedFamily, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Family invitation accepted successfully.", mapOf("family" to updatedFamily, "members" to listMembers(updatedFamily))))
    }

    fun rejectJoinRequest(request: RejectJoinRequestRequest): ResponseEntity<*> {
        val head = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = head.familyId?.let { familyRepository.findById(it).orElse(null) } ?: return ApiResponseUtil.badRequest("Not in a family")
        if (family.headId != head.id) return ApiResponseUtil.forbidden("Only head can reject")
        val requester = userRepository.findAll().find { it.id == request.requesterId } ?: return ApiResponseUtil.notFound("User not found")
        val updatedFamily = if (family.pendingJoinRequests.contains(requester.id)) {
            family.copy(pendingJoinRequests = (family.pendingJoinRequests - requester.id).toMutableList(), updatedAt = now()).also { familyRepository.save(it) }
        } else family
        // update join request record if exists
        val pendingRecord = joinRequestRepository.findByRequesterIdAndFamilyIdAndStatus(requester.id, family.familyId, JoinRequestStatus.PENDING)
        if (pendingRecord != null) {
            joinRequestRepository.save(pendingRecord.copy(status = JoinRequestStatus.REJECTED, updatedAt = now()))
        }
        notifyJoinRequestRejected(requester, updatedFamily, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Join request rejected", mapOf("family" to updatedFamily, "members" to listMembers(updatedFamily))))
    }

    fun acceptJoinRequest(request: AcceptJoinRequestRequest): ResponseEntity<*> {
        val head = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = head.familyId?.let { familyRepository.findById(it).orElse(null) } ?: return ApiResponseUtil.badRequest("Not in a family")
        if (family.headId != head.id) return ApiResponseUtil.forbidden("Only head can accept")
        val requester = userRepository.findAll().find { it.id == request.requesterId } ?: return ApiResponseUtil.notFound("User not found")
        if (requester.familyId != null) return ApiResponseUtil.conflict("User already in a family")
        if (family.membersIds.size >= family.maxSize) return ApiResponseUtil.conflict("Family full")
        if (!family.pendingJoinRequests.contains(requester.id)) {
            // fall back: ensure there is a pending repo request else error
            val pendingRecord = joinRequestRepository.findByRequesterIdAndFamilyIdAndStatus(requester.id, family.familyId, JoinRequestStatus.PENDING)
                ?: return ApiResponseUtil.notFound("No pending join request")
        }
        val updatedFamily = family.copy(
            membersIds = (family.membersIds + requester.id).toMutableList(),
            pendingJoinRequests = (family.pendingJoinRequests - requester.id).toMutableList(),
            updatedAt = now()
        )
        familyRepository.save(updatedFamily)
        userRepository.save(requester.copy(familyId = family.familyId, updatedAt = now()))
        val pendingRecord = joinRequestRepository.findByRequesterIdAndFamilyIdAndStatus(requester.id, family.familyId, JoinRequestStatus.PENDING)
        if (pendingRecord != null) {
            joinRequestRepository.save(pendingRecord.copy(status = JoinRequestStatus.ACCEPTED, updatedAt = now(), processedBy = head.id))
        }
        notifyJoinRequestAccepted(requester, updatedFamily, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Join request accepted", mapOf("family" to updatedFamily, "members" to listMembers(updatedFamily))))
    }

    fun removeMember(request: RemoveMemberRequest): ResponseEntity<*> {
        validateEmail(request.memberEmail)?.let { return ApiResponseUtil.badRequest(it) }
        val head = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val family = head.familyId?.let { familyRepository.findById(it).orElse(null) } ?: return ApiResponseUtil.badRequest("Not in a family")
        if (family.headId != head.id) return ApiResponseUtil.forbidden("Only head can remove")
        val member = userRepository.findAll().find { it.email == request.memberEmail } ?: return ApiResponseUtil.notFound("User not found")
        if (member.familyId != family.familyId || !family.membersIds.contains(member.id)) return ApiResponseUtil.badRequest("Member not in this family")
        if (member.id == head.id) return ApiResponseUtil.badRequest("Head cannot remove self")
        val updated = family.copy(membersIds = (family.membersIds - member.id).toMutableList(), updatedAt = now())
        familyRepository.save(updated)
        userRepository.save(member.copy(familyId = null, updatedAt = now()))
        notifyMemberRemoved(member, updated, head)
        return ResponseEntity.ok(BasicFamilySuccessResponse("Member removed from family successfully", mapOf("family" to updated, "members" to listMembers(updated))))
    }

    /* ===================== Validation Helpers ===================== */

    private fun validateFamilyName(name: String): String? = when {
        name.isBlank() -> "Family name cannot be blank"
        name.length < FamilyDtoConstraints.FAMILY_NAME_MIN_LENGTH -> "Family name must be at least ${FamilyDtoConstraints.FAMILY_NAME_MIN_LENGTH} characters"
        name.length > FamilyDtoConstraints.FAMILY_NAME_MAX_LENGTH -> "Family name cannot exceed ${FamilyDtoConstraints.FAMILY_NAME_MAX_LENGTH} characters"
        name.trim() != name -> "Family name cannot have leading or trailing spaces"
        !name.matches(Regex("^[a-zA-Z0-9\\s\\-_]+")) -> "Family name contains invalid characters"
        else -> null
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email cannot be blank"
        !email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) -> "Invalid email format"
        email.length > 254 -> "Email is too long"
        else -> null
    }

    private fun validateAliasName(alias: String): String? = when {
        alias.isBlank() -> "Alias name cannot be blank"
        alias.length != FamilyDtoConstraints.FAMILY_ALIAS_LENGTH -> "Alias name must be exactly ${FamilyDtoConstraints.FAMILY_ALIAS_LENGTH} characters"
        !alias.matches(Regex("^[A-Z0-9]+$")) -> "Alias name must contain only uppercase letters and numbers"
        else -> null
    }

    /* ===================== Internal Helpers (notifications, persistence) ===================== */

    private fun generateUniqueAliasName(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val existing = familyRepository.findAll().map { it.aliasName }.toSet()
        repeat(MAX_GENERATION_ATTEMPTS) {
            val candidate = (1..FamilyDtoConstraints.FAMILY_ALIAS_LENGTH).map { chars.random() }.joinToString("")
            if (!existing.contains(candidate)) return candidate
        }
        throw IllegalStateException("Unable to generate unique alias name")
    }

    private fun createNotification(
        title: String,
        message: String,
        familyId: String,
        senderName: String,
        senderId: String,
        receiverId: String,
        type: NotificationType,
        familyAliasName: String,
        actionable: Boolean = false
    ) = Notification(
        title = title.take(255),
        message = message.take(1000),
        timestamp = now(),
        isRead = false,
        familyId = familyId.take(50),
        familyAlias = familyAliasName.take(10),
        senderName = senderName.take(100),
        senderId = senderId.take(50),
        receiverId = receiverId.take(50),
        actionable = actionable,
        type = type
    )

    private fun saveNotification(notification: Notification): Notification? = try {
        notificationRepository.save(notification)
    } catch (ex: Exception) {
        logger.warn("Failed saving notification: ${ex.message}"); null
    }

    private fun sendDataPush(token: String?, type: NotificationType, title: String, body: String, data: Map<String, String>, notificationId: Long?) {
        if (token.isNullOrBlank()) return
        val enriched = if (notificationId != null) data + ("notificationId" to notificationId.toString()) else data
        familyNotificationService.sendToSingle(token, type, title, body, enriched, tag = enriched["familyId"] ?: enriched["familyAlias"]) }

    private fun sendInvitationNotification(invitedUser: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val notif = createNotification(
            "Family Invitation",
            "You have been invited to join the family '${family.name}' by ${headUser.name}.",
            family.familyId,
            headUser.name ?: headUser.email,
            headUser.id,
            invitedUser.id,
            NotificationType.JOIN_FAMILY_INVITATION,
            family.aliasName,
            true
        )
        val saved = saveNotification(notif)
        sendDataPush(invitedUser.fcmToken, NotificationType.JOIN_FAMILY_INVITATION, "Family Invitation", "You have been invited to join the family '${family.name}' by ${headUser.name}.", mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "invited_member_email" to headUser.email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id
        ), saved?.id)
    }

    private fun notifyInvitationCancelled(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val notif = createNotification("Invitation Cancelled", "The invitation to join the family '${family.name}' has been cancelled.", family.familyId, headUser.name ?: headUser.email, headUser.id, user.id, NotificationType.JOIN_FAMILY_INVITATION_CANCELLED, family.aliasName)
        val saved = saveNotification(notif)
        sendDataPush(user.fcmToken, NotificationType.JOIN_FAMILY_INVITATION_CANCELLED, "Invitation Cancelled", "The invitation to join the family '${family.name}' has been cancelled.", mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_email" to headUser.email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id
        ), saved?.id)
    }

    private fun notifyInvitationAccepted(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val userName = user.name ?: user.email
        val notif = createNotification("Invitation Accepted", "$userName (${user.email}) has accepted your invitation to join the family '${family.name}'.", family.familyId, userName, user.id, headUser.id, NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED, family.aliasName)
        val saved = saveNotification(notif)
        sendDataPush(headUser.fcmToken, NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED, "Invitation Accepted", "$userName (${user.email}) has accepted your invitation to join the family '${family.name}'.", mapOf(
            "alias_name" to family.aliasName,
            "sender_name" to userName,
            "family_name" to family.name,
            "sender_email" to user.email,
            "sender_id" to user.id
        ), saved?.id)
    }

    private fun notifyInvitationRejected(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val userName = user.name ?: user.email
        val notif = createNotification("Invitation Rejected", "$userName (${user.email}) has rejected your invitation to join the family '${family.name}'.", family.familyId, userName, user.id, headUser.id, NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED, family.aliasName)
        val saved = saveNotification(notif)
        sendDataPush(headUser.fcmToken, NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED, "Invitation Rejected", "$userName (${user.email}) has rejected your invitation to join the family '${family.name}'.", mapOf(
            "alias_name" to family.aliasName,
            "sender_name" to userName,
            "family_name" to family.name,
            "sender_email" to user.email,
            "sender_id" to user.id
        ), saved?.id)
    }

    private fun notifyJoinRequestRejected(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val notif = createNotification("Join Request Rejected", "Your request to join the family '${family.name}' has been rejected.", family.familyId, headUser.name ?: headUser.email, headUser.id, user.id, NotificationType.JOIN_FAMILY_REQUEST_REJECTED, family.aliasName)
        val saved = saveNotification(notif)
        sendDataPush(user.fcmToken, NotificationType.JOIN_FAMILY_REQUEST_REJECTED, "Join Request Rejected", "Your request to join the family '${family.name}' has been rejected.", mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_email" to headUser.email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id
        ), saved?.id)
    }

    private fun notifyJoinRequestAccepted(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val notif = createNotification("Join Request Accepted", "Your request to join the family '${family.name}' has been accepted.", family.familyId, headUser.name ?: headUser.email, headUser.id, user.id, NotificationType.JOIN_FAMILY_REQUEST_ACCEPTED, family.aliasName)
        val saved = saveNotification(notif)
        sendDataPush(user.fcmToken, NotificationType.JOIN_FAMILY_REQUEST_ACCEPTED, "Join Request Accepted", "Your request to join the family '${family.name}' has been accepted.", mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_email" to headUser.email,
            "sender_id" to headUser.id,
            "sender_name" to (headUser.name ?: "")
        ), saved?.id)
    }

    private fun notifyMemberRemoved(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val notif = createNotification("Removed from Family", "You have been removed from the family '${family.name}' by the family head.", family.familyId, headUser.name ?: headUser.email, headUser.id, user.id, NotificationType.FAMILY_MEMBER_REMOVED, family.aliasName)
        val saved = saveNotification(notif)
        sendDataPush(user.fcmToken, NotificationType.FAMILY_MEMBER_REMOVED, "Removed from Family", "You have been removed from the family '${family.name}' by the family head.", mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_email" to headUser.email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id
        ), saved?.id)
    }

    /* ===================== Utility ===================== */

    private fun listMembers(family: Family) = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }
    private fun currentUserOr404(): ExpenseUser? = try { userRepository.findById(authUtil.getCurrentUserId()).orElse(null) } catch (_: Exception) { null }
    private fun now() = System.currentTimeMillis()

    private fun computeJoinRequestThrottle(userId: String, familyId: String): Map<String, Any>? {
        val now = now()
        val attempts = joinRequestRepository.findByRequesterIdAndFamilyIdOrderByCreatedAtDesc(userId, familyId)
        if (attempts.isEmpty()) return null
        val recent = attempts.filter { now - it.createdAt <= ATTEMPT_WINDOW_MS }
        // Count only non-cancelled attempts in window
        val counted = recent.filter { it.status != JoinRequestStatus.CANCELLED }
        return if (counted.size >= MAX_ATTEMPTS_PER_WINDOW) {
            mapOf(
                "error" to "CONFLICT",
                "message" to "Max retries over. Ask family owner to send",
                "reason" to "MAX_RETRIES",
                "attemptsInWindow" to counted.size,
                "windowMillis" to ATTEMPT_WINDOW_MS,
                "maxAttemptsPerWindow" to MAX_ATTEMPTS_PER_WINDOW,
                "windowStart" to (now - ATTEMPT_WINDOW_MS)
            )
        } else null
    }

    fun cancelOwnJoinRequestById(request: JoinRequestByIdActionRequest): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val jr = joinRequestRepository.findById(request.requestId).orElse(null)
            ?: return ApiResponseUtil.notFound("Join request not found")
        if (jr.requesterId != user.id) return ApiResponseUtil.forbidden("Not owner of request")
        if (jr.status != JoinRequestStatus.PENDING) return ApiResponseUtil.conflict("Join request is not pending")
        val family = familyRepository.findById(jr.familyId).orElse(null)
            ?: return ApiResponseUtil.notFound("Family not found")
        val cancelled = jr.copy(status = JoinRequestStatus.CANCELLED, updatedAt = now())
        joinRequestRepository.save(cancelled)
        if (family.pendingJoinRequests.contains(user.id)) {
            familyRepository.save(family.copy(pendingJoinRequests = (family.pendingJoinRequests - user.id).toMutableList(), updatedAt = now()))
        }
        return ResponseEntity.ok(mapOf("message" to "Join request cancelled", "request" to cancelled))
    }

    fun resendJoinRequestById(request: JoinRequestByIdActionRequest): ResponseEntity<*> {
        val user = currentUserOr404() ?: return ApiResponseUtil.notFound("User not found")
        val jr = joinRequestRepository.findById(request.requestId).orElse(null)
            ?: return ApiResponseUtil.notFound("Join request not found")
        if (jr.requesterId != user.id) return ApiResponseUtil.forbidden("Not owner of request")
        if (jr.status == JoinRequestStatus.ACCEPTED) return ApiResponseUtil.conflict("Already accepted")
        val family = familyRepository.findById(jr.familyId).orElse(null)
            ?: return ApiResponseUtil.notFound("Family not found")
        if (user.familyId != null && user.familyId == family.familyId) return ApiResponseUtil.conflict("Already in family")
        if (family.membersIds.size >= family.maxSize) return ApiResponseUtil.conflict("Family full")
        val throttle = computeJoinRequestThrottle(user.id, family.familyId)
        if (throttle != null) return ResponseEntity.status(409).body(throttle)
        // cancel previous pending ones
        val existing = joinRequestRepository.findByRequesterIdAndFamilyIdOrderByCreatedAtDesc(user.id, family.familyId).filter { it.status == JoinRequestStatus.PENDING }
        existing.forEach { prev -> joinRequestRepository.save(prev.copy(status = JoinRequestStatus.CANCELLED, updatedAt = now())) }
        val ensuredFamily = if (!family.pendingJoinRequests.contains(user.id)) {
            val updated = family.copy(pendingJoinRequests = (family.pendingJoinRequests + user.id).toMutableList(), updatedAt = now())
            familyRepository.save(updated); updated
        } else family
        val newReq = JoinRequest(
            id = UUID.randomUUID().toString(),
            requesterId = user.id,
            familyId = ensuredFamily.familyId,
            message = request.message,
            status = JoinRequestStatus.PENDING,
            createdAt = now(),
            updatedAt = now()
        )
        val saved = joinRequestRepository.save(newReq)
        notifyHeadNewJoinRequest(user, ensuredFamily, saved)
        return ResponseEntity.ok(mapOf("message" to "Join request sent", "requestId" to saved.id))
    }
}
