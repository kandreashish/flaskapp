package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.model.Family
import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.NotificationType
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.ApiResponseUtil
import com.lavish.expensetracker.util.AuthUtil
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/family")
class FamilyController @Autowired constructor(
    private val familyRepository: FamilyRepository,
    private val userRepository: ExpenseUserRepository,
    private val pushNotificationService: PushNotificationService,
    private val authUtil: AuthUtil,
    private val notificationRepository: NotificationRepository,
) {
    private val logger = LoggerFactory.getLogger(FamilyController::class.java)

    companion object {
        private const val FAMILY_ALIAS_LENGTH = 6
        private const val FAMILY_MAX_SIZE = 10
        private const val MAX_GENERATION_ATTEMPTS = 100
        private const val FAMILY_NAME_MAX_LENGTH = 100
        private const val FAMILY_NAME_MIN_LENGTH = 2
    }

    // Data classes for request validation
    data class CreateFamilyRequest(
        @field:NotBlank(message = "Family name is required")
        @field:Size(
            min = FAMILY_NAME_MIN_LENGTH,
            max = FAMILY_NAME_MAX_LENGTH,
            message = "Family name must be between $FAMILY_NAME_MIN_LENGTH and $FAMILY_NAME_MAX_LENGTH characters"
        )
        val familyName: String
    )

    data class JoinFamilyRequest(
        @field:NotBlank(message = "Alias name is required")
        @field:Size(
            min = FAMILY_ALIAS_LENGTH,
            max = FAMILY_ALIAS_LENGTH,
            message = "Alias name must be exactly $FAMILY_ALIAS_LENGTH characters"
        )
        val aliasName: String
    )

    data class InviteMemberRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val invitedMemberEmail: String
    )

    data class AcceptJoinRequestRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val requesterEmail: String
    )

    data class RemoveMemberRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val memberEmail: String
    )

    data class RejectJoinRequestRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val requesterEmail: String
    )

    // Validation helper methods
    private fun validateFamilyName(familyName: String): String? {
        return when {
            familyName.isBlank() -> "Family name cannot be blank"
            familyName.length < FAMILY_NAME_MIN_LENGTH -> "Family name must be at least $FAMILY_NAME_MIN_LENGTH characters"
            familyName.length > FAMILY_NAME_MAX_LENGTH -> "Family name cannot exceed $FAMILY_NAME_MAX_LENGTH characters"
            familyName.trim() != familyName -> "Family name cannot have leading or trailing spaces"
            !familyName.matches(Regex("^[a-zA-Z0-9\\s\\-_]+$")) -> "Family name contains invalid characters"
            else -> null
        }
    }

    private fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "Email cannot be blank"
            !email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) -> "Invalid email format"
            email.length > 254 -> "Email is too long"
            else -> null
        }
    }

    private fun validateAliasName(aliasName: String): String? {
        return when {
            aliasName.isBlank() -> "Alias name cannot be blank"
            aliasName.length != FAMILY_ALIAS_LENGTH -> "Alias name must be exactly $FAMILY_ALIAS_LENGTH characters"
            !aliasName.matches(Regex("^[A-Z0-9]+$")) -> "Alias name must contain only uppercase letters and numbers"
            else -> null
        }
    }

    // Optimized logging methods
    private fun logError(message: String, operation: String = "", userId: String? = null): String {
        val fullMessage = buildString {
            append(message)
            if (operation.isNotEmpty()) append(" during $operation")
            if (userId != null) append(" (User: $userId)")
        }
        logger.warn(fullMessage)
        return fullMessage
    }

    private fun logUserNotFound(userId: String, operation: String = "") =
        logError("User not found with ID: $userId", operation, userId)

    private fun logUserAlreadyInFamily(userId: String, familyId: String, operation: String = "") =
        logError("User already belongs to family: $familyId", operation, userId)

    private fun logUserNotInFamily(userId: String, operation: String = "") =
        logError("User does not belong to any family", operation, userId)

    private fun logFamilyNotFound(familyId: String, operation: String = "") =
        logError("Family not found with ID: $familyId", operation)

    private fun logFamilyNotFoundByAlias(aliasName: String, operation: String = "") =
        logError("Family not found with alias: $aliasName", operation)

    private fun logFamilyFull(familyId: String, currentSize: Int, maxSize: Int, operation: String = "") =
        logError("Family $familyId is full ($currentSize/$maxSize)", operation)

    private fun logNotFamilyHead(userId: String, headId: String, operation: String = "") =
        logError("User is not the family head. Head ID: $headId", operation, userId)

    // Optimized database operations
    private fun findUserByEmail(email: String) = userRepository.findAll().find { it.email == email }
    private fun findFamilyByAlias(aliasName: String) = familyRepository.findAll().find { it.aliasName == aliasName }

    // Optimized notification creation
    private fun createNotification(
        title: String,
        message: String,
        familyId: String,
        senderName: String,
        senderId: String,
        type: NotificationType,
        familyAliasName: String,
        actionable: Boolean = false
    ): Notification {
        return Notification(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            time = System.currentTimeMillis(),
            read = false,
            familyId = familyId,
            senderName = senderName,
            senderId = senderId,
            actionable = actionable,
            createdAt = System.currentTimeMillis(),
            type = type,
            familyAliasName = familyAliasName
        )
    }

    // Optimized push notification sending with error handling
    private fun sendPushNotification(
        token: String?,
        title: String,
        message: String,
        data: Map<String, String>,
        recipientEmail: String
    ) {
        if (token.isNullOrBlank()) {
            logger.warn("FCM token is null or empty for user: $recipientEmail")
            return
        }

        try {
            pushNotificationService.sendNotificationWithData(token, title, message, data)
            logger.info("Push notification sent successfully to: $recipientEmail")
        } catch (ex: Exception) {
            logger.error("Failed to send push notification to: $recipientEmail", ex)
        }
    }

    @PostMapping("/create")
    fun createFamily(@Valid @RequestBody request: CreateFamilyRequest): ResponseEntity<*> {
        logger.info("Creating family with name: ${request.familyName}")

        return try {
            // Validate family name
            validateFamilyName(request.familyName)?.let { error ->
                logger.warn("Invalid family name: $error")
                return ApiResponseUtil.badRequest(error)
            }

            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(userId, "family creation"))

            if (user.familyId != null) {
                return ApiResponseUtil.conflict(logUserAlreadyInFamily(userId, user.familyId, "family creation"))
            }

            val familyId = UUID.randomUUID().toString()
            val aliasName = generateUniqueAliasName()

            val family = Family(
                familyId = familyId,
                headId = userId,
                name = request.familyName.trim(),
                aliasName = aliasName,
                maxSize = FAMILY_MAX_SIZE,
                membersIds = mutableListOf(userId),
                updatedAt = System.currentTimeMillis()
            )

            familyRepository.save(family)
            userRepository.save(user.copy(familyId = familyId, updatedAt = System.currentTimeMillis()))

            logger.info("Family created successfully with ID: $familyId")
            ResponseEntity.ok(family)
        } catch (ex: Exception) {
            logger.error("Exception in createFamily", ex)
            ApiResponseUtil.internalServerError("An error occurred while creating family")
        }
    }

    fun generateUniqueAliasName(): String {
        logger.debug("Generating unique alias name")

        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var attempts = 0
        val existingAliases = familyRepository.findAll().map { it.aliasName }.toSet()

        while (attempts < MAX_GENERATION_ATTEMPTS) {
            val alias = (1..FAMILY_ALIAS_LENGTH)
                .map { chars.random() }
                .joinToString("")

            if (!existingAliases.contains(alias)) {
                logger.info("Unique alias generated: $alias after ${attempts + 1} attempts")
                return alias
            }
            attempts++
        }

        logger.error("Failed to generate unique alias after $MAX_GENERATION_ATTEMPTS attempts")
        throw RuntimeException("Unable to generate unique alias name")
    }

    @PostMapping("/join")
    fun joinFamily(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> {
        logger.info("User attempting to join family with alias: ${request.aliasName}")

        return try {
            // Validate alias name
            validateAliasName(request.aliasName)?.let { error ->
                logger.warn("Invalid alias name: $error")
                return ApiResponseUtil.badRequest(error)
            }

            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(userId, "family join"))

            if (user.familyId != null) {
                return ApiResponseUtil.conflict(logUserAlreadyInFamily(userId, user.familyId, "family join"))
            }

            val family = findFamilyByAlias(request.aliasName)
                ?: return ApiResponseUtil.notFound(logFamilyNotFoundByAlias(request.aliasName, "family join"))

            if (family.membersIds.size >= family.maxSize) {
                return ApiResponseUtil.conflict(
                    logFamilyFull(
                        family.familyId,
                        family.membersIds.size,
                        family.maxSize,
                        "family join"
                    )
                )
            }

            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userId).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )

            familyRepository.save(updatedFamily)
            userRepository.save(user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis()))

            logger.info("User $userId successfully joined family ${family.familyId}")
            ResponseEntity.ok(updatedFamily)
        } catch (ex: Exception) {
            logger.error("Exception in joinFamily", ex)
            ApiResponseUtil.internalServerError("An error occurred while joining family")
        }
    }

    @PostMapping("/leave")
    fun leaveFamily(): ResponseEntity<*> {
        logger.info("User attempting to leave family")

        return try {
            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(userId, "family leave"))

            val familyId = user.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(userId, "family leave"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "family leave"))

            if (!family.membersIds.contains(userId)) {
                return ApiResponseUtil.badRequest("User is not a member of this family")
            }

            val newMembers = family.membersIds.toMutableList().apply { remove(userId) }

            if (newMembers.isEmpty()) {
                familyRepository.delete(family)
                logger.info("Family $familyId deleted as no members remain")
            } else {
                val newHeadId = if (family.headId == userId) newMembers.first() else family.headId
                familyRepository.save(
                    family.copy(
                        membersIds = newMembers,
                        headId = newHeadId,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // Notify family head if user is leaving
                if (family.headId != userId) {
                    notifyFamilyHead(family, user, "has left the family")
                }
            }

            userRepository.save(user.copy(familyId = null, updatedAt = System.currentTimeMillis()))
            logger.info("User $userId successfully left family $familyId")

            ResponseEntity.ok(mapOf("message" to "Left family successfully"))
        } catch (ex: Exception) {
            logger.error("Exception in leaveFamily", ex)
            ApiResponseUtil.internalServerError("An error occurred while leaving family")
        }
    }

    @GetMapping("/details")
    fun getFamilyDetails(): ResponseEntity<*> {
        logger.info("Getting family details")

        return try {
            val currentUserId = authUtil.getCurrentUserId()
            val user = userRepository.findById(currentUserId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(currentUserId, "get family details"))

            val familyId = user.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(currentUserId, "get family details"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "get family details"))

            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to family,
                "members" to members
            )

            logger.info("Family details retrieved successfully")
            ResponseEntity.ok(response)
        } catch (ex: Exception) {
            logger.error("Exception in getFamilyDetails", ex)
            ApiResponseUtil.internalServerError("An error occurred while getting family details")
        }
    }

    @PostMapping("/invite")
    fun inviteMember(@Valid @RequestBody request: InviteMemberRequest): ResponseEntity<*> {
        logger.info("Inviting member with email: ${request.invitedMemberEmail}")

        return try {
            validateEmail(request.invitedMemberEmail)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val currentUserId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(currentUserId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(currentUserId, "invite member"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(currentUserId, "invite member"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "invite member"))

            if (family.headId != currentUserId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(currentUserId, family.headId, "invite member"))
            }

            val invitedUser = findUserByEmail(request.invitedMemberEmail)
                ?: return ApiResponseUtil.notFound("Invited user not found")

            if (invitedUser.familyId != null) {
                return ApiResponseUtil.conflict("Invited user already belongs to a family")
            }

            if (family.pendingMemberEmails.contains(request.invitedMemberEmail)) {
                return ApiResponseUtil.conflict("User already invited and pending")
            }

            val updatedFamily = family.copy(
                pendingMemberEmails = (family.pendingMemberEmails + request.invitedMemberEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Send invitation notification
            sendInvitationNotification(invitedUser, family, headUser)

            logger.info("Member invitation completed successfully")
            ResponseEntity.ok(mapOf("message" to "Invitation sent to ${request.invitedMemberEmail} and is pending acceptance"))
        } catch (ex: Exception) {
            logger.error("Exception in inviteMember", ex)
            ApiResponseUtil.internalServerError("An error occurred while inviting member")
        }
    }

    // Helper method for sending invitation notifications
    private fun sendInvitationNotification(invitedUser: Any, family: Family, headUser: Any) {
        val title = "Family Invitation"
        val message =
            "You have been invited to join the family '${family.name}' by ${(headUser as com.lavish.expensetracker.model.ExpenseUser).name}. Please accept the invitation to join."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "invited_member_email" to headUser.email,
            "sender_id" to headUser.id,
            "type" to NotificationType.FAMILY_INVITE.name
        )

        sendPushNotification(
            (invitedUser as com.lavish.expensetracker.model.ExpenseUser).fcmToken,
            title,
            message,
            data,
            invitedUser.email
        )

        val notification = createNotification(
            title,
            message,
            family.familyId,
            headUser.name ?: headUser.email,
            headUser.id,
            NotificationType.FAMILY_INVITE,
            family.aliasName,
            true
        )
        notificationRepository.save(notification)
    }

    // Helper method for notifying family head
    private fun notifyFamilyHead(family: Family, user: Any, action: String) {
        val headUser = userRepository.findById(family.headId).orElse(null) ?: return
        val userName = (user as com.lavish.expensetracker.model.ExpenseUser).name ?: user.email

        val title = "Family Member Update"
        val message = "$userName $action '${family.name}'."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_name" to user.email,
            "sender_id" to user.id,
            "type" to NotificationType.OTHER.name
        )

        sendPushNotification(headUser.fcmToken, title, message, data, headUser.email)

        val notification = createNotification(
            title,
            message,
            family.familyId,
            userName,
            user.id,
            NotificationType.OTHER,
            family.aliasName
        )
        notificationRepository.save(notification)
    }

    @PostMapping("/request-join")
    fun requestToJoinFamily(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> {
        logger.info("User requesting to join family with alias: ${request.aliasName}")

        return try {
            validateAliasName(request.aliasName)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(userId, "request to join family"))

            if (user.familyId != null) {
                return ApiResponseUtil.conflict(logUserAlreadyInFamily(userId, user.familyId, "request to join family"))
            }

            val family = findFamilyByAlias(request.aliasName)
                ?: return ApiResponseUtil.notFound(
                    logFamilyNotFoundByAlias(
                        request.aliasName,
                        "request to join family"
                    )
                )

            if (family.membersIds.size >= family.maxSize) {
                return ApiResponseUtil.conflict(
                    logFamilyFull(
                        family.familyId,
                        family.membersIds.size,
                        family.maxSize,
                        "request to join family"
                    )
                )
            }

            if (family.pendingJoinRequests.contains(user.email)) {
                return ApiResponseUtil.conflict("You have already requested to join this family")
            }

            val updatedFamily = family.copy(
                pendingJoinRequests = (family.pendingJoinRequests + user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Notify family head
            notifyFamilyHeadOfJoinRequest(family, user)

            logger.info("Join request completed successfully")
            ResponseEntity.ok(mapOf("message" to "Join request sent to family head"))
        } catch (ex: Exception) {
            logger.error("Exception in requestToJoinFamily", ex)
            ApiResponseUtil.internalServerError("An error occurred while requesting to join family")
        }
    }

    private fun notifyFamilyHeadOfJoinRequest(family: Family, user: Any) {
        val headUser = userRepository.findById(family.headId).orElse(null) ?: return
        val userName = (user as com.lavish.expensetracker.model.ExpenseUser).name ?: user.email

        val title = "Join Request"
        val message = "$userName (${user.email}) has requested to join your family '${family.name}'."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "requester_email" to user.email,
            "sender_id" to user.id,
            "type" to NotificationType.FAMILY_JOIN.name
        )

        sendPushNotification(headUser.fcmToken, title, message, data, headUser.email)

        val notification = createNotification(
            title,
            message,
            family.familyId,
            userName,
            user.id,
            NotificationType.FAMILY_JOIN,
            family.aliasName
        )
        notificationRepository.save(notification)
    }

    @PostMapping("/accept-join-request")
    fun acceptJoinRequest(@Valid @RequestBody request: AcceptJoinRequestRequest): ResponseEntity<*> {
        logger.info("Accepting join request for: ${request.requesterEmail}")

        return try {
            validateEmail(request.requesterEmail)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val headId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(headId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(headId, "accept join request"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(headId, "accept join request"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "accept join request"))

            if (family.headId != headId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(headId, family.headId, "accept join request"))
            }

            if (!family.pendingJoinRequests.contains(request.requesterEmail)) {
                return ApiResponseUtil.notFound("No such join request pending")
            }

            val userToAdd = findUserByEmail(request.requesterEmail)
                ?: return ApiResponseUtil.notFound("User not found")

            if (userToAdd.familyId != null) {
                return ApiResponseUtil.conflict("User already belongs to a family")
            }

            if (family.membersIds.size >= family.maxSize) {
                return ApiResponseUtil.conflict(
                    logFamilyFull(
                        family.familyId,
                        family.membersIds.size,
                        family.maxSize,
                        "accept join request"
                    )
                )
            }

            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userToAdd.id).toMutableList(),
                pendingJoinRequests = (family.pendingJoinRequests - request.requesterEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            userRepository.save(userToAdd.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis()))

            // Notify the user
            notifyJoinRequestAccepted(userToAdd, family, headUser)

            logger.info("Join request accepted successfully")
            ResponseEntity.ok(mapOf("message" to "User added to family and notified"))
        } catch (ex: Exception) {
            logger.error("Exception in acceptJoinRequest", ex)
            ApiResponseUtil.internalServerError("An error occurred while accepting join request")
        }
    }

    private fun notifyJoinRequestAccepted(user: Any, family: Family, headUser: Any) {
        val title = "Join Request Accepted"
        val message = "Your request to join the family '${family.name}' has been accepted."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_name" to (headUser as com.lavish.expensetracker.model.ExpenseUser).email,
            "sender_id" to headUser.id,
            "type" to NotificationType.FAMILY_JOIN.name
        )

        sendPushNotification(
            (user as com.lavish.expensetracker.model.ExpenseUser).fcmToken,
            title,
            message,
            data,
            user.email
        )

        val notification = createNotification(
            title,
            message,
            family.familyId,
            headUser.name ?: headUser.email,
            headUser.id,
            NotificationType.FAMILY_JOIN,
            family.aliasName
        )
        notificationRepository.save(notification)
    }

    @PostMapping("/accept-invitation")
    fun acceptFamilyInvitation(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> {
        logger.info("Accepting family invitation for alias: ${request.aliasName}")

        return try {
            validateAliasName(request.aliasName)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(userId, "accept family invitation"))

            if (user.familyId != null) {
                return ApiResponseUtil.conflict(
                    logUserAlreadyInFamily(
                        userId,
                        user.familyId,
                        "accept family invitation"
                    )
                )
            }

            val family = findFamilyByAlias(request.aliasName)
                ?: return ApiResponseUtil.notFound(
                    logFamilyNotFoundByAlias(
                        request.aliasName,
                        "accept family invitation"
                    )
                )

            if (!family.pendingMemberEmails.contains(user.email)) {
                return ApiResponseUtil.notFound("No invitation found for this user")
            }

            if (family.membersIds.size >= family.maxSize) {
                return ApiResponseUtil.conflict(
                    logFamilyFull(
                        family.familyId,
                        family.membersIds.size,
                        family.maxSize,
                        "accept family invitation"
                    )
                )
            }

            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userId).toMutableList(),
                pendingMemberEmails = (family.pendingMemberEmails - user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            userRepository.save(user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis()))

            // Notify the head
            notifyInvitationAccepted(user, family)

            logger.info("Family invitation accepted successfully")
            ResponseEntity.ok(mapOf("message" to "Joined family and head notified"))
        } catch (ex: Exception) {
            logger.error("Exception in acceptFamilyInvitation", ex)
            ApiResponseUtil.internalServerError("An error occurred while accepting invitation")
        }
    }


    private fun notifyInvitationAccepted(user: Any, family: Family) {
        val headUser = userRepository.findById(family.headId).orElse(null) ?: return
        val userName = (user as com.lavish.expensetracker.model.ExpenseUser).name ?: user.email

        val title = "Invitation Accepted"
        val message = "$userName (${user.email}) has accepted your invitation to join the family '${family.name}'."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_name" to user.email,
            "sender_id" to user.id,
            "type" to NotificationType.FAMILY_JOIN.name
        )

        sendPushNotification(headUser.fcmToken, title, message, data, headUser.email)

        val notification = createNotification(
            title,
            message,
            family.familyId,
            userName,
            user.id,
            NotificationType.FAMILY_JOIN,
            family.aliasName,
        )
        notificationRepository.save(notification)
    }

    @PostMapping("/reject-join-request")
    fun rejectJoinRequest(@Valid @RequestBody request: RejectJoinRequestRequest): ResponseEntity<*> {
        logger.info("Rejecting join request for: ${request.requesterEmail}")

        return try {
            validateEmail(request.requesterEmail)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val headId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(headId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(headId, "reject join request"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(headId, "reject join request"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "reject join request"))

            if (family.headId != headId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(headId, family.headId, "reject join request"))
            }

            if (!family.pendingJoinRequests.contains(request.requesterEmail)) {
                return ApiResponseUtil.notFound("No pending join request found for this email")
            }

            val updatedFamily = family.copy(
                pendingJoinRequests = (family.pendingJoinRequests - request.requesterEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Notify the requester about rejection
            val requesterUser = findUserByEmail(request.requesterEmail)
            if (requesterUser != null) {
                notifyJoinRequestRejected(requesterUser, family, headUser)
            }

            logger.info("Join request rejected successfully")
            ResponseEntity.ok(mapOf("message" to "Join request rejected successfully"))
        } catch (ex: Exception) {
            logger.error("Exception in rejectJoinRequest", ex)
            ApiResponseUtil.internalServerError("An error occurred while rejecting join request")
        }
    }

    private fun notifyJoinRequestRejected(user: Any, family: Family, headUser: Any) {
        val title = "Join Request Rejected"
        val message = "Your request to join the family '${family.name}' has been rejected."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_name" to (headUser as com.lavish.expensetracker.model.ExpenseUser).email,
            "sender_id" to headUser.id,
            "type" to NotificationType.FAMILY_INVITE.name
        )

        sendPushNotification(
            (user as com.lavish.expensetracker.model.ExpenseUser).fcmToken,
            title,
            message,
            data,
            user.email
        )

        val notification = createNotification(
            title,
            message,
            family.familyId,
            headUser.name ?: headUser.email,
            headUser.id,
            NotificationType.FAMILY_INVITE,
            family.aliasName
        )
        notificationRepository.save(notification)
    }

    @PostMapping("/remove-member")
    fun removeFamilyMember(@Valid @RequestBody request: RemoveMemberRequest): ResponseEntity<*> {
        logger.info("Removing member with email: ${request.memberEmail}")

        return try {
            validateEmail(request.memberEmail)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val headId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(headId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(headId, "remove family member"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(headId, "remove family member"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "remove family member"))

            if (family.headId != headId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(headId, family.headId, "remove family member"))
            }

            val memberToRemove = findUserByEmail(request.memberEmail)
                ?: return ApiResponseUtil.notFound("Member not found")

            if (memberToRemove.familyId != familyId) {
                return ApiResponseUtil.badRequest("Member does not belong to this family")
            }

            if (memberToRemove.id == headId) {
                return ApiResponseUtil.badRequest("Family head cannot remove themselves. Use leave family instead.")
            }

            if (!family.membersIds.contains(memberToRemove.id)) {
                return ApiResponseUtil.badRequest("Member is not part of this family")
            }

            val updatedFamily = family.copy(
                membersIds = (family.membersIds - memberToRemove.id).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            userRepository.save(memberToRemove.copy(familyId = null, updatedAt = System.currentTimeMillis()))

            // Notify the removed member
            notifyMemberRemoved(memberToRemove, family, headUser)

            logger.info("Member removed successfully")
            ResponseEntity.ok(mapOf("message" to "Member removed from family successfully"))
        } catch (ex: Exception) {
            logger.error("Exception in removeFamilyMember", ex)
            ApiResponseUtil.internalServerError("An error occurred while removing family member")
        }
    }

    private fun notifyMemberRemoved(user: Any, family: Family, headUser: Any) {
        val title = "Removed from Family"
        val message = "You have been removed from the family '${family.name}' by the family head."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_name" to (headUser as com.lavish.expensetracker.model.ExpenseUser).email,
            "sender_id" to headUser.id,
            "type" to NotificationType.OTHER.name
        )

        sendPushNotification(
            (user as com.lavish.expensetracker.model.ExpenseUser).fcmToken,
            title,
            message,
            data,
            user.email
        )

        val notification = createNotification(
            title,
            message,
            family.familyId,
            headUser.name ?: headUser.email,
            headUser.id,
            NotificationType.OTHER,
            family.aliasName
        )
        notificationRepository.save(notification)
    }
}
