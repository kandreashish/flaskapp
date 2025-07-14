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

    data class CancelInvitationRequest(
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Valid email is required")
        val invitedMemberEmail: String
    )

    data class BasicFamilySuccessResponse(
        val message: String,
        val family: Map<String, Any>
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
    @Operation(summary = "Create a new family", description = "Allows a user to create a new family with a unique name")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Family created successfully", content = [Content(mediaType = "application/json", schema = Schema(implementation = Family::class))]),
            ApiResponse(responseCode = "400", description = "Invalid input", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, user already in family", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
        ]
    )
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
    @Operation(summary = "Join an existing family", description = "Allows a user to join a family using the family's alias")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully joined the family", content = [Content(mediaType = "application/json", schema = Schema(implementation = Family::class))]),
            ApiResponse(responseCode = "400", description = "Invalid input", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, family is full or user already in family", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
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
            ResponseEntity.ok(updatedFamily)
        } catch (ex: Exception) {
            logger.error("Exception in joinFamily", ex)
            ApiResponseUtil.internalServerError("An error occurred while joining family")
        }
    }

    @PostMapping("/leave")
    @Operation(summary = "Leave the current family", description = "Allows a user to leave their current family")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully left the family", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
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
                ResponseEntity.ok(mapOf("message" to "Left family successfully - family deleted as no members remain"))
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

                userRepository.save(user.copy(familyId = null, updatedAt = System.currentTimeMillis()))
                logger.info("User $userId successfully left family $familyId")
                val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

                val response = mapOf(
                    "family" to updatedFamily,
                    "members" to members
                )


                ResponseEntity.ok(BasicFamilySuccessResponse("Left family successfully", response))
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
            ApiResponse(responseCode = "200", description = "Family details retrieved successfully", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
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
    @Operation(summary = "Invite a member to the family", description = "Sends an invitation to a user to join the family")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Invitation sent successfully", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid email format", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, user already in family or invitation pending", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
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
            ResponseEntity.ok(BasicFamilySuccessResponse("Invitation sent to ${request.invitedMemberEmail} and is pending acceptance", response))
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
    @Operation(summary = "Request to join a family", description = "Allows a user to request joining a family by sending a join request")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join request sent successfully", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid alias name", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, user already in family or request pending", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
        ]
    )
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
            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to family,
                "members" to members
            )

            logger.info("Join request completed successfully")
            ResponseEntity.ok(BasicFamilySuccessResponse("Join request sent to family head", response))
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
    @Operation(summary = "Accept a join request", description = "Allows the family head to accept a join request from a user")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join request accepted and user added to family", content = [Content(mediaType = "application/json", schema = Schema(implementation = Family::class))]),
            ApiResponse(responseCode = "400", description = "Invalid email format", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, user already in family or request not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
        ]
    )
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

            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to family,
                "members" to members
            )

            logger.info("Join request accepted successfully")
            ResponseEntity.ok(BasicFamilySuccessResponse("User added to family and notified", response))
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
    @Operation(summary = "Accept a family invitation", description = "Allows a user to accept an invitation to join a family")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Invitation accepted and user joined the family", content = [Content(mediaType = "application/json", schema = Schema(implementation = Family::class))]),
            ApiResponse(responseCode = "400", description = "Invalid alias name", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, user already in family or invitation not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
        ]
    )
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
            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )

            logger.info("Family invitation accepted successfully")
            ResponseEntity.ok(BasicFamilySuccessResponse("Joined family and head notified", response))
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
            family.aliasName
        )
        notificationRepository.save(notification)
    }

    @PostMapping("/reject-join-request")
    @Operation(summary = "Reject a join request", description = "Allows the family head to reject a join request from a user")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join request rejected successfully", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid email format", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, request not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
        ]
    )
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
            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }

            val response = mapOf(
                "family" to updatedFamily,
                "members" to members
            )

            logger.info("Join request rejected successfully")
            ResponseEntity.ok(BasicFamilySuccessResponse("Join request rejected successfully", response))
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
    @Operation(summary = "Remove a member from the family", description = "Allows the family head to remove a member from the family")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Member removed successfully", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid email format", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, member not in family", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
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

    @PostMapping("/cancel-invitation")
    @Operation(summary = "Cancel an invitation", description = "Allows the family head to cancel a pending invitation")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Invitation cancelled successfully", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid email format", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, no pending invitation found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
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
            ApiResponse(responseCode = "200", description = "Invitation resent successfully", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "400", description = "Invalid email format", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "404", description = "User or family not found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "409", description = "Conflict, no pending invitation found", content = [Content(mediaType = "application/json")]),
            ApiResponse(responseCode = "500", description = "Internal server error", content = [Content(mediaType = "application/json")])
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

            ResponseEntity.ok(BasicFamilySuccessResponse("Invitation resent to ${request.invitedMemberEmail} successfully", response))
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
}
