package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.model.Family
import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.NotificationType
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.ApiResponseUtil
import com.lavish.expensetracker.util.AuthUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(
    name = "Family Management",
    description = "Complete family management API including creation, member management, invitations, and join requests"
)
@SecurityRequirement(name = "Bearer Authentication")
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
        val familyName: String,
    )

    data class JoinFamilyRequest(
        @field:NotBlank(message = "Alias name is required")
        @field:Size(
            min = FAMILY_ALIAS_LENGTH,
            max = FAMILY_ALIAS_LENGTH,
            message = "Alias name must be exactly $FAMILY_ALIAS_LENGTH characters"
        )
        val aliasName: String,
        val notificationId: Long?
    )

    data class InviteMemberRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val invitedMemberEmail: String,
        val notificationId: Long?
    )

    data class AcceptJoinRequestRequest(
        @field:NotBlank(message = "Email is required")
        @field:NotBlank(message = "requesterId is required")
        val requesterId: String,
        val notificationId: Long?
    )

    data class RemoveMemberRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val memberEmail: String,
        val notificationId: Long?
    )

    data class RejectJoinRequestRequest(
        @field:NotBlank(message = "Email is required")
        @field:NotBlank(message = "requesterId is required")
        val requesterId: String,
        val notificationId: Long?
    )

    data class CancelInvitationRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val invitedMemberEmail: String,
        val notificationId: Long?
    )

    data class BasicFamilySuccessResponse(
        val message: String,
        val family: Map<String, Any>
    )

    data class UpdateFamilyNameRequest(
        @field:NotBlank(message = "Family name is required")
        @field:Size(
            min = FAMILY_NAME_MIN_LENGTH,
            max = FAMILY_NAME_MAX_LENGTH,
            message = "Family name must be between $FAMILY_NAME_MIN_LENGTH and $FAMILY_NAME_MAX_LENGTH characters"
        )
        val familyName: String
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
    private fun findUserByUserId(userId: String) = userRepository.findAll().find { it.id == userId }
    private fun findFamilyByAlias(aliasName: String) = familyRepository.findByAliasName(aliasName)

    // Simplified notification creation
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
    ): Notification {
        return Notification(
            title = title.take(255),
            message = message.take(1000),
            timestamp = System.currentTimeMillis(),
            isRead = false,
            familyId = familyId.take(50),
            familyAlias = familyAliasName.take(10),
            senderName = senderName.take(100),
            senderId = senderId.take(50),
            receiverId = receiverId.take(50),
            actionable = actionable,
            type = type
        )
    }

    // Simple notification saving
    private fun saveNotificationSafely(notification: Notification): Boolean {
        return try {
            notificationRepository.save(notification)
            logger.debug("Notification saved successfully")
            true
        } catch (ex: Exception) {
            logger.warn("Failed to save notification: ${ex.message}")
            false
        }
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
    @Operation(summary = "Create a new family", description = "Allows a user to create a new family with a unique name")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Family created successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = Family::class))]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid input",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, user already in family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun createFamily(@Valid @RequestBody request: CreateFamilyRequest): ResponseEntity<*> {
        logger.info("Creating family with name: ${request.familyName}")

        return try {
            // Validate family name
            validateFamilyName(request.familyName)?.let { error ->
                logger.warn("Invalid family1 name: $error")
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

            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to family,
                "members" to members
            )

            ResponseEntity.ok(BasicFamilySuccessResponse("Family created successfully", response))

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
    @Operation(
        summary = "Join an existing family",
        description = "Allows a user to join a family using the family's alias"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully joined the family",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = Family::class))]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid input",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, family is full or user already in family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
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
            ResponseEntity.ok(
                BasicFamilySuccessResponse(
                    "Joined family successfully",
                    mapOf(
                        "family" to updatedFamily,
                        "members" to updatedFamily.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }
                    )
                ))
        } catch (ex: Exception) {
            logger.error("Exception in joinFamily", ex)
            ApiResponseUtil.internalServerError("An error occurred while joining family")
        }
    }

    @PostMapping("/leave")
    @Operation(summary = "Leave the current family", description = "Allows a user to leave their current family")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully left the family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid request",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
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
                userRepository.save(user.copy(familyId = null, updatedAt = System.currentTimeMillis()))
                ResponseEntity.ok(mapOf("message" to "Family deleted"))
            } else {
                val newHeadId = if (family.headId == userId) newMembers.first() else family.headId
                val updatedFamily = family.copy(
                    membersIds = newMembers,
                    headId = newHeadId,
                    updatedAt = System.currentTimeMillis()
                )
                familyRepository.save(updatedFamily)
                // Notify family head if user is leaving
                if (family.headId != userId) {
                    notifyFamilyHead(family, user, "has left the family")
                }

                logger.info("User $userId successfully left family $familyId")
                userRepository.save(user.copy(familyId = null, updatedAt = System.currentTimeMillis()))
                ResponseEntity.ok(mapOf("message" to "Left family successfully"))
            }

        } catch (ex: Exception) {
            logger.error("Exception in leaveFamily", ex)
            ApiResponseUtil.internalServerError("An error occurred while leaving family")
        }
    }

    @GetMapping("/details")
    @Operation(summary = "Get family details", description = "Retrieves the details of the current user's family")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Family details retrieved successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid request",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
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
    @Operation(
        summary = "Invite a member to the family",
        description = "Sends an invitation to a user to join the family"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Invitation sent successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid email format",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, user already in family or invitation pending",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
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

            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )

            logger.info("Member invitation completed successfully")
            ResponseEntity.ok(
                BasicFamilySuccessResponse(
                    "Invitation sent to ${request.invitedMemberEmail} and is pending acceptance",
                    response
                )
            )
        } catch (ex: Exception) {
            logger.error("Exception in inviteMember", ex)
            ApiResponseUtil.internalServerError("An error occurred while inviting member")
        }
    }

    // Helper method for sending invitation notifications
    private fun sendInvitationNotification(invitedUser: Any, family: Family, headUser: Any) {
        val title = "Family Invitation"
        val message =
            "You have been invited to join the family '${family.name}' by ${(headUser as ExpenseUser).name}. Please accept the invitation to join."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "invited_member_email" to headUser.email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id,
            "type" to NotificationType.JOIN_FAMILY_INVITATION.name
        )

        sendPushNotification(
            (invitedUser as ExpenseUser).fcmToken,
            title,
            message,
            data,
            invitedUser.email
        )

        try {
            val notification = createNotification(
                title,
                message,
                family.familyId,
                headUser.name ?: headUser.email,
                headUser.id,
                invitedUser.id,
                NotificationType.JOIN_FAMILY_INVITATION,
                family.aliasName,
                true
            )
            notificationRepository.save(notification)
            logger.info("Notification saved successfully for invitation")
        } catch (ex: Exception) {
            logger.error("Failed to save notification for invitation: ${ex.message}", ex)
            // Don't fail the entire invitation process if notification saving fails
        }
    }

    // Helper method for notifying family head
    private fun notifyFamilyHead(family: Family, user: Any, action: String) {
        val headUser = userRepository.findById(family.headId).orElse(null) ?: return
        val userName = (user as ExpenseUser).name ?: user.email

        val title = "Family Member Update"
        val message = "$userName $action '${family.name}'."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_name" to user.email,
            "sender_id" to user.id,
            "type" to NotificationType.FAMILY_MEMBER_LEFT.name
        )

        sendPushNotification(headUser.fcmToken, title, message, data, headUser.email)

        val notification = createNotification(
            title,
            message,
            family.familyId,
            userName,
            user.id,
            headUser.id,
            NotificationType.FAMILY_MEMBER_LEFT,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    // Helper method for notifying family head of join request
    private fun notifyFamilyHeadOfJoinRequest(family: Family, user: Any) {
        val headUser = userRepository.findById(family.headId).orElse(null) ?: return
        val userName = (user as ExpenseUser).name ?: user.email

        val title = "Join Request"
        val message = "$userName (${user.email}) has requested to join your family '${family.name}'."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "requester_email" to user.email,
            "sender_id" to user.id,
            "sender_name" to userName,
            "type" to NotificationType.JOIN_FAMILY_REQUEST.name
        )

        sendPushNotification(headUser.fcmToken, title, message, data, headUser.email)

        val notification = createNotification(
            title,
            message,
            family.familyId,
            userName,
            user.id,
            headUser.id,
            NotificationType.JOIN_FAMILY_REQUEST,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    // Helper method for notifying join request acceptance
    private fun notifyJoinRequestAccepted(user: Any, family: Family, headUser: Any) {
        val title = "Join Request Accepted"
        val message = "Your request to join the family '${family.name}' has been accepted."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_email" to (headUser as ExpenseUser).email,
            "sender_id" to headUser.id,
            "sender_name" to (headUser.name ?: ""),
            "type" to NotificationType.JOIN_FAMILY_REQUEST_ACCEPTED.name
        )

        sendPushNotification(
            (user as ExpenseUser).fcmToken,
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
            user.id,
            NotificationType.JOIN_FAMILY_REQUEST_ACCEPTED,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    // Helper method for notifying invitation acceptance
    private fun notifyInvitationAccepted(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val userName = user.name ?: user.email
        val title = "Invitation Accepted"
        val message = "$userName (${user.email}) has accepted your invitation to join the family '${family.name}'."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "sender_name" to userName,
            "family_name" to family.name,
            "sender_email" to user.email,
            "sender_id" to user.id,
            "type" to NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED.name
        )

        sendPushNotification(headUser.fcmToken, title, message, data, headUser.email)

        val notification = createNotification(
            title,
            message,
            family.familyId,
            userName,
            user.id,
            headUser.id,
            NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    // Helper method for notifying join request rejection
    private fun notifyJoinRequestRejected(user: Any, family: Family, headUser: Any) {
        val title = "Join Request Rejected"
        val message = "Your request to join the family '${family.name}' has been rejected."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_email" to (headUser as ExpenseUser).email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id,
            "type" to NotificationType.JOIN_FAMILY_REQUEST_REJECTED.name
        )

        sendPushNotification(
            (user as ExpenseUser).fcmToken,
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
            user.id,
            NotificationType.JOIN_FAMILY_REQUEST_REJECTED,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    @PostMapping("/remove-member")
    @Operation(
        summary = "Remove a member from the family",
        description = "Allows the family head to remove a member from the family"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Member removed successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid email format",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, member not in family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
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
            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )


            logger.info("Member removed successfully")
            ResponseEntity.ok(BasicFamilySuccessResponse("Member removed from family successfully", response))
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
            "sender_email" to (headUser as ExpenseUser).email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id,
            "type" to NotificationType.FAMILY_MEMBER_REMOVED.name
        )

        sendPushNotification(
            (user as ExpenseUser).fcmToken,
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
            user.id,
            NotificationType.FAMILY_MEMBER_REMOVED,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    @PostMapping("/cancel-invitation")
    @Operation(summary = "Cancel an invitation", description = "Allows the family head to cancel a pending invitation")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Invitation cancelled successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid email format",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, no pending invitation found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun cancelInvitation(@Valid @RequestBody request: CancelInvitationRequest): ResponseEntity<*> {
        logger.info("Cancelling invitation for member with email: ${request.invitedMemberEmail}")

        return try {
            validateEmail(request.invitedMemberEmail)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val currentUserId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(currentUserId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(currentUserId, "cancel invitation"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(currentUserId, "cancel invitation"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "cancel invitation"))

            if (family.headId != currentUserId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(currentUserId, family.headId, "cancel invitation"))
            }

            if (!family.pendingMemberEmails.contains(request.invitedMemberEmail)) {
                return ApiResponseUtil.notFound("No pending invitation found for this email")
            }

            val updatedFamily = family.copy(
                pendingMemberEmails = (family.pendingMemberEmails - request.invitedMemberEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Notify the user about cancellation
            val invitedUser = findUserByEmail(request.invitedMemberEmail)
            if (invitedUser != null) {
                notifyInvitationCancelled(invitedUser, family, headUser)
            }
            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )

            logger.info("Invitation cancelled successfully")
            ResponseEntity.ok(BasicFamilySuccessResponse("Invitation cancelled successfully", response))
        } catch (ex: Exception) {
            logger.error("Exception in cancelInvitation", ex)
            ApiResponseUtil.internalServerError("An error occurred while cancelling invitation")
        }
    }

    @PostMapping("/resend-invitation")
    @Operation(summary = "Resend an invitation", description = "Allows the family head to resend a pending invitation")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Invitation resent successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid email format",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, no pending invitation found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun resendInvitation(@Valid @RequestBody request: InviteMemberRequest): ResponseEntity<*> {
        logger.info("Resending invitation for member with email: ${request.invitedMemberEmail}")

        return try {
            validateEmail(request.invitedMemberEmail)?.let { error ->
                return ApiResponseUtil.badRequest(error)
            }

            val currentUserId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(currentUserId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(currentUserId, "resend invitation"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(currentUserId, "resend invitation"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "resend invitation"))

            if (family.headId != currentUserId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(currentUserId, family.headId, "resend invitation"))
            }

            if (!family.pendingMemberEmails.contains(request.invitedMemberEmail)) {
                return ApiResponseUtil.notFound("No pending invitation found for this email. Please send a new invitation instead.")
            }

            val invitedUser = findUserByEmail(request.invitedMemberEmail)
                ?: return ApiResponseUtil.notFound("Invited user not found")

            if (invitedUser.familyId != null) {
                return ApiResponseUtil.conflict("Invited user already belongs to a family")
            }

            // Resend invitation notification
            sendInvitationNotification(invitedUser, family, headUser)

            logger.info("Invitation resent successfully")
            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to family,
                "members" to members
            )

            ResponseEntity.ok(
                BasicFamilySuccessResponse(
                    "Invitation resent to ${request.invitedMemberEmail} successfully",
                    response
                )
            )
        } catch (ex: Exception) {
            logger.error("Exception in resendInvitation", ex)
            ApiResponseUtil.internalServerError("An error occurred while resending invitation")
        }
    }

    private fun notifyInvitationCancelled(user: Any, family: Family, headUser: Any) {
        val title = "Invitation Cancelled"
        val message = "The invitation to join the family '${family.name}' has been cancelled."

        val data = mapOf(
            "alias_name" to family.aliasName,
            "family_name" to family.name,
            "sender_email" to (headUser as ExpenseUser).email,
            "sender_name" to (headUser.name ?: ""),
            "sender_id" to headUser.id,
            "type" to NotificationType.JOIN_FAMILY_INVITATION_CANCELLED.name
        )

        sendPushNotification(
            (user as ExpenseUser).fcmToken,
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
            user.id,
            NotificationType.JOIN_FAMILY_INVITATION_CANCELLED,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    @PostMapping("/reject-invitation")
    @Operation(
        summary = "Reject a family invitation",
        description = "Allows a user to reject an invitation to join a family"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Invitation rejected successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Bad request or user not invited to this family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun rejectInvitation(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> {
        logger.info("Rejecting family invitation for alias: ${request.aliasName}")

        return try {
            val userId = authUtil.getCurrentUserId()
            if (request.notificationId != null) {
                notificationRepository.findById(request.notificationId).ifPresent {
                    val updatedNotification = it.copy(isRead = true, actionable = false)
                    notificationRepository.save(updatedNotification)
                }
            }
            val user = userRepository.findById(userId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(userId, "reject family invitation"))

            // Find the family by alias
            val family = familyRepository.findByAliasName(request.aliasName.trim())
                ?: return ApiResponseUtil.notFound(
                    logFamilyNotFoundByAlias(
                        request.aliasName,
                        "reject family invitation"
                    )
                )

            // Check if the user has a pending invitation for this family
            if (!family.pendingMemberEmails.contains(user.email)) {
                if (request.notificationId != null) {
                    val notification = notificationRepository.findById(
                        request.notificationId
                    )

                    if (notification.isEmpty) {
                        return ApiResponseUtil.notFound("No pending invitation found for this user")
                    }

                    if (notification.isPresent && notification.get().type == NotificationType.JOIN_FAMILY_INVITATION) {
                        return ApiResponseUtil.badRequest("You have already accepted or rejected this invitation")
                    }
                }

                return ApiResponseUtil.badRequest("No pending invitation found for this user")
            }

            // Remove the user's email from pending invitations
            val updatedFamily = family.copy(
                pendingMemberEmails = (family.pendingMemberEmails - user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Get family head user for notification
            val headUser = userRepository.findById(family.headId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(family.headId, "reject family invitation"))
            //mark previously accepted invitation as read
            // Notify the family head about the rejection
            sendInvitationRejectedNotification(user, updatedFamily, headUser)

            val response = mapOf(
                "message" to "Family invitation rejected successfully",
                "familyName" to family.name,
                "aliasName" to family.aliasName
            )

            logger.info("Family invitation rejected successfully")
            ResponseEntity.ok(BasicFamilySuccessResponse("Family invitation rejected successfully", response))

        } catch (ex: Exception) {
            logger.error("Exception in rejectInvitation", ex)
            ApiResponseUtil.internalServerError("An error occurred while rejecting invitation")
        }
    }

    // Helper method for sending invitation rejection notifications
    private fun sendInvitationRejectedNotification(user: ExpenseUser, family: Family, headUser: ExpenseUser) {
        val userName = user.name ?: user.email
        val title = "Invitation Rejected"
        val message = "$userName (${user.email}) has rejected your invitation to join the family '${family.name}'."

        val data = mapOf(
            "type" to "invitation_rejected",
            "familyId" to family.familyId,
            "familyName" to family.name,
            "rejectedUserEmail" to user.email,
            "rejectedUserName" to userName
        )

        pushNotificationService.sendNotificationWithData(
            headUser.fcmToken,
            title,
            message,
            data
        )

        val notification = createNotification(
            title,
            message,
            family.familyId,
            userName,
            user.id,
            headUser.id,
            NotificationType.JOIN_FAMILY_INVITATION_REJECTED,
            family.aliasName
        )
        saveNotificationSafely(notification)
    }

    @PostMapping("/update-name")
    @Operation(
        summary = "Update family name",
        description = "Allows the family head to update the family's name"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Family name updated successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Invalid family name",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, family name already in use",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun updateFamilyName(@Valid @RequestBody request: UpdateFamilyNameRequest): ResponseEntity<*> {
        logger.info("Updating family name to: ${request.familyName}")

        return try {
            // Validate family name
            validateFamilyName(request.familyName)?.let { error ->
                logger.warn("Invalid family name: $error")
                return ApiResponseUtil.badRequest(error)
            }

            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(userId, "update family name"))

            val familyId = user.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(userId, "update family name"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "update family name"))

            if (family.headId != userId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(userId, family.headId, "update family name"))
            }

            // Check for name conflict with existing families (exclude current family)
            val existingFamilyWithName = familyRepository.findAll()
                .find { it.name.equals(request.familyName.trim(), ignoreCase = true) && it.familyId != familyId }

            if (existingFamilyWithName != null) {
                return ApiResponseUtil.conflict("Family name already in use")
            }

            val updatedFamily = family.copy(
                name = request.familyName.trim(),
                updatedAt = System.currentTimeMillis()
            )

            familyRepository.save(updatedFamily)

            logger.info("Family name updated successfully to: ${request.familyName}")

            ResponseEntity.ok(
                BasicFamilySuccessResponse(
                    "Family name updated successfully",
                    mapOf("family" to updatedFamily)
                )
            )
        } catch (ex: Exception) {
            logger.error("Exception in updateFamilyName", ex)
            ApiResponseUtil.internalServerError("An error occurred while updating family name")
        }
    }

    @PostMapping("/accept-invitation")
    @Operation(
        summary = "Accept a family invitation",
        description = "Allows a user to accept an invitation to join a family"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Invitation accepted successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Bad request or user not invited to this family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User or family not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, family is full or user already in family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun acceptInvitation(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> {
        logger.info("Accepting family invitation for alias: ${request.aliasName}")

        return try {
            val userId = authUtil.getCurrentUserId()
            if (request.notificationId != null) {
                notificationRepository.findById(request.notificationId).ifPresent {
                    val updatedNotification = it.copy(isRead = true, actionable = false)
                    notificationRepository.save(updatedNotification)
                }
            }
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

            // Find the family by alias
            val family = familyRepository.findByAliasName(request.aliasName.trim())
                ?: return ApiResponseUtil.notFound(
                    logFamilyNotFoundByAlias(
                        request.aliasName,
                        "accept family invitation"
                    )
                )

            // Check if the user has a pending invitation for this family
            if (!family.pendingMemberEmails.contains(user.email)) {

                if (request.notificationId != null) {
                    val notification = notificationRepository.findById(request.notificationId)

                    if (notification.isEmpty) {
                        return ApiResponseUtil.notFound("No pending invitation found for this user")
                    }

                    if (notification.isPresent && notification.get().type != NotificationType.JOIN_FAMILY_INVITATION) {
                        return ApiResponseUtil.badRequest("Invalid invitation notification")
                    }
                }

                return ApiResponseUtil.badRequest("No pending invitation found for this user")
            }

            // Check if family is full
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

            // Add user to family and remove from pending invitations
            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userId).toMutableList(),
                pendingMemberEmails = (family.pendingMemberEmails - user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Update user's family ID
            userRepository.save(user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis()))

            // Mark the invitation notification as read

            // Get family head user for notification
            val headUser = userRepository.findById(family.headId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(family.headId, "accept family invitation"))

            // Notify the family head about the acceptance
            notifyInvitationAccepted(user, updatedFamily, headUser)

            val members = updatedFamily.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )

            logger.info("Family invitation accepted successfully")
            ResponseEntity.ok(
                BasicFamilySuccessResponse(
                    "Family invitation accepted successfully. Welcome to ${family.name}!",
                    response
                )
            )

        } catch (ex: Exception) {
            logger.error("Exception in acceptInvitation", ex)
            ApiResponseUtil.internalServerError("An error occurred while accepting invitation")
        }
    }

    @PostMapping("/reject-join-request")
    @Operation(
        summary = "Reject a join request",
        description = "Allows the family head to reject a user's request to join the family"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Join request rejected successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "412",
                description = "Bad request or invalid email format",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden, user is not the family head",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User, family, or join request not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun rejectJoinRequest(@Valid @RequestBody request: RejectJoinRequestRequest): ResponseEntity<*> {
        logger.info("Rejecting join request for user: ${request.requesterId}")

        return try {
            val headId = authUtil.getCurrentUserId()
            if (request.notificationId != null) {
                notificationRepository.findById(request.notificationId).ifPresent {
                    val updatedNotification = it.copy(isRead = true, actionable = false)
                    notificationRepository.save(updatedNotification)
                }
            }
            val headUser = userRepository.findById(headId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(headId, "reject join request"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(headId, "reject join request"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "reject join request"))

            if (family.headId != headId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(headId, family.headId, "reject join request"))
            }

            val requesterUser = findUserByUserId(request.requesterId)
                ?: return ApiResponseUtil.notFound("Requester user not found")

            // Check if there's a pending join request from this user
            if (!family.pendingJoinRequests.contains(requesterUser.email)) {
                return ApiResponseUtil.notFound("No pending join request found for this user")
            }

            // Remove the user's email from pending join requests
            val updatedFamily = family.copy(
                pendingJoinRequests = (family.pendingJoinRequests - requesterUser.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Notify the requester about the rejection
            notifyJoinRequestRejected(requesterUser, updatedFamily, headUser)

            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )

            logger.info("Join request rejected successfully for user: ${requesterUser.email}")

            ResponseEntity.ok(
                BasicFamilySuccessResponse(
                    "Join request from ${request.requesterId} has been rejected",
                    response
                )
            )

        } catch (ex: Exception) {
            logger.error("Exception in rejectJoinRequest", ex)
            ApiResponseUtil.internalServerError("An error occurred while rejecting join request")
        }
    }


    @PostMapping("/accept-join-request")
    @Operation(
        summary = "Accept a join request",
        description = "Allows the family head to accept a user's request to join the family"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Join request accepted successfully",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad request or invalid email format",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden, user is not the family head",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "User, family, or join request not found",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict, family is full or user already in family",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content(mediaType = "application/json")]
            )
        ]
    )
    fun acceptJoinRequest(@Valid @RequestBody request: AcceptJoinRequestRequest): ResponseEntity<*> {
        logger.info("Accepting join request for user: ${request.requesterId}")

        return try {
            val headId = authUtil.getCurrentUserId()
            if (request.notificationId != null) {
                notificationRepository.findById(request.notificationId).ifPresent {
                    val updatedNotification = it.copy(isRead = true, actionable = false)
                    notificationRepository.save(updatedNotification)
                }
            }
            val headUser = userRepository.findById(headId).orElse(null)
                ?: return ApiResponseUtil.notFound(logUserNotFound(headId, "accept join request"))

            val familyId = headUser.familyId
                ?: return ApiResponseUtil.badRequest(logUserNotInFamily(headId, "accept join request"))

            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ApiResponseUtil.notFound(logFamilyNotFound(familyId, "accept join request"))

            if (family.headId != headId) {
                return ApiResponseUtil.forbidden(logNotFamilyHead(headId, family.headId, "accept join request"))
            }

            val requesterUser = findUserByUserId(request.requesterId)
                ?: return ApiResponseUtil.notFound("Requester user not found")

            // Check if there's a pending join request from this user
            if (!family.pendingJoinRequests.contains(requesterUser.email)) {
                return ApiResponseUtil.notFound("No pending join request found for this user")
            }

            // Check if requester already belongs to a family
            if (requesterUser.familyId != null) {
                return ApiResponseUtil.conflict("User already belongs to a family")
            }

            // Check if family is full
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

            // Add user to family and remove from pending join requests
            val updatedFamily = family.copy(
                membersIds = (family.membersIds + requesterUser.id).toMutableList(),
                pendingJoinRequests = (family.pendingJoinRequests - requesterUser.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            // Update user's family ID
            userRepository.save(requesterUser.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis()))

            // Notify the requester about the acceptance
            notifyJoinRequestAccepted(requesterUser, updatedFamily, headUser)

            val members = updatedFamily.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )

            logger.info("Join request accepted successfully for user: ${request.requesterId}")
            ResponseEntity.ok(
                BasicFamilySuccessResponse(
                    "Join request from ${requesterUser.email} has been accepted. Welcome to ${family.name}!",
                    response
                )
            )

        } catch (ex: Exception) {
            logger.error("Exception in acceptJoinRequest", ex)
            ApiResponseUtil.internalServerError("An error occurred while accepting join request")
        }
    }
}

