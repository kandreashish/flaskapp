package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.model.Family
import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.NotificationType
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.AuthUtil
import com.lavish.expensetracker.util.ApiResponseUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*
import kotlin.text.contains
import kotlin.toString

@RestController
@RequestMapping("/api/family")
class FamilyController @Autowired constructor(
    private val familyRepository: FamilyRepository,
    private val userRepository: ExpenseUserRepository,
    @Autowired private val pushNotificationService: PushNotificationService,
    private val authUtil: AuthUtil,
    private val notificationRepository: NotificationRepository,
) {
    private val logger = LoggerFactory.getLogger(FamilyController::class.java)

    @PostMapping("/create")
    fun createFamily(
        @RequestParam familyName: String
    ): ResponseEntity<*> {
        logger.info("Creating family with name: $familyName")
        return try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $userId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            if (user.familyId != null) {
                logger.warn("User $userId already belongs to family: ${user.familyId}")
                return ApiResponseUtil.conflict("User already belongs to a family")
            }

            val familyId = UUID.randomUUID().toString()
            val aliasName = generateUniqueAliasName()
            logger.info("Generated family ID: $familyId, alias: $aliasName")

            val family = Family(
                familyId = familyId,
                headId = userId,
                name = familyName,
                aliasName = aliasName,
                maxSize = 2,
                membersIds = mutableListOf(userId),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(family)
            logger.info("Family saved successfully with ID: $familyId")

            val updatedUser = user.copy(familyId = familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            logger.info("User updated with family ID: $familyId")

            logger.info("Family creation completed successfully for user: $userId")
            ResponseEntity.ok(family)
        } catch (ex: Exception) {
            logger.error("Exception in createFamily for user: ${authUtil.getCurrentUserId()}, familyName: $familyName", ex)
            ApiResponseUtil.internalServerError("An error occurred while creating family")
        }
    }

    fun generateUniqueAliasName(): String {
        logger.debug("Generating unique alias name")
        return try {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            var alias: String
            var attempts = 0
            do {
                alias = (1..6)
                    .map { chars.random() }
                    .joinToString("")
                attempts++
                logger.debug("Generated alias attempt $attempts: $alias")
            } while (familyRepository.findAll().any { it.aliasName == alias })
            logger.info("Unique alias generated: $alias after $attempts attempts")
            alias
        } catch (ex: Exception) {
            logger.error("Exception in generateUniqueAliasName", ex)
            throw ex
        }
    }

    @PostMapping("/join")
    fun joinFamily(
        @RequestParam aliasName: String
    ): ResponseEntity<*> {
        logger.info("User attempting to join family with alias: $aliasName")
        return try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $userId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            if (user.familyId != null) {
                logger.warn("User $userId already belongs to family: ${user.familyId}")
                return ApiResponseUtil.conflict("User already belongs to a family")
            }

            val family = familyRepository.findAll().find { it.aliasName == aliasName }
            if (family == null) {
                logger.warn("Family not found with alias: $aliasName")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name} (ID: ${family.familyId}) with ${family.membersIds.size} members")

            if (family.membersIds.size >= family.maxSize) {
                logger.warn("Family ${family.familyId} is full (${family.membersIds.size}/${family.maxSize})")
                return ApiResponseUtil.conflict("Family is full")
            }

            val updatedFamily = family.copy(
                membersIds = (family.membersIds.toMutableList() + userId).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            logger.info("Family ${family.familyId} updated with new member: $userId")

            val updatedUser = user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            logger.info("User $userId updated with family ID: ${family.familyId}")

            logger.info("User $userId successfully joined family ${family.familyId}")
            ResponseEntity.ok(updatedFamily)
        } catch (ex: Exception) {
            logger.error("Exception in joinFamily for user: ${authUtil.getCurrentUserId()}, alias: $aliasName", ex)
            ApiResponseUtil.internalServerError("An error occurred while joining family")
        }
    }

    @PostMapping("/leave")
    fun leaveFamily(): ResponseEntity<*> {
        logger.info("User attempting to leave family")
        return try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $userId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            val familyId = user.familyId
            if (familyId == null) {
                logger.warn("User $userId does not belong to any family")
                return ApiResponseUtil.badRequest("User does not belong to a family")
            }
            logger.info("User belongs to family: $familyId")

            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("Family not found with ID: $familyId")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name} with ${family.membersIds.size} members")

            if (!family.membersIds.contains(userId)) {
                logger.warn("User $userId is not a member of family $familyId")
                return ApiResponseUtil.badRequest("User is not a member of this family")
            }

            val newMembers = family.membersIds.toMutableList().apply { remove(userId) }
            logger.info("Removing user $userId from family. New member count: ${newMembers.size}")

            if (newMembers.isEmpty()) {
                logger.info("Family $familyId will be deleted as no members remain")
                familyRepository.delete(family)
                logger.info("Family $familyId deleted successfully")
            } else {
                val newHeadId = if (family.headId == userId) newMembers.first() else family.headId
                if (family.headId == userId) {
                    logger.info("Family head changed from $userId to $newHeadId")
                }

                val updatedFamily = family.copy(
                    membersIds = newMembers,
                    headId = newHeadId,
                    updatedAt = System.currentTimeMillis()
                )
                familyRepository.save(updatedFamily)
                logger.info("Family $familyId updated with new member list and head: $newHeadId")
            }

            val updatedUser = user.copy(familyId = null, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            logger.info("User $userId family association removed")

            // Send push notification to the family head
            if (family.headId != userId) {
                logger.info("Sending notification to family head: ${family.headId}")
                val headUser = userRepository.findById(family.headId).orElse(null)
                if (headUser == null) {
                    logger.warn("Family head not found with ID: ${family.headId}")
                    return ApiResponseUtil.notFound("Family head not found")
                }

                try {
                    pushNotificationService.sendNotification(
                        headUser.fcmToken,
                        "Family Member Left",
                        "${user.name ?: user.email} has left the family '${family.name}'."
                    )
                    logger.info("Push notification sent to family head: ${family.headId}")
                } catch (ex: Exception) {
                    logger.error("Failed to send push notification to family head: ${family.headId}", ex)
                }

                // Save notification in database for the family head
                val notification = Notification(
                    id = UUID.randomUUID().toString(),
                    title = "Family Member Left",
                    message = "${user.name ?: user.email} has left the family '${family.name}'.",
                    time = System.currentTimeMillis(),
                    read = false,
                    familyId = family.familyId,
                    senderName = user.name ?: user.email,
                    senderId = user.id,
                    actionable = false,
                    createdAt = System.currentTimeMillis(),
                    type = NotificationType.OTHER
                )
                notificationRepository.save(notification)
                logger.info("Notification saved for family head: ${family.headId}")
            }

            logger.info("User $userId successfully left family $familyId")
            ResponseEntity.ok(mapOf("message" to "Left family successfully"))
        } catch (ex: Exception) {
            logger.error("Exception in leaveFamily for user: ${authUtil.getCurrentUserId()}", ex)
            ApiResponseUtil.internalServerError("An error occurred while leaving family")
        }
    }

    @GetMapping("/details")
    fun getFamilyDetails(): ResponseEntity<*> {
        logger.info("Getting family details")
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $currentUserId")

            val user = userRepository.findById(currentUserId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $currentUserId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            val familyId = user.familyId
            if (familyId == null) {
                logger.warn("User $currentUserId does not belong to any family")
                return ApiResponseUtil.badRequest("User does not belong to a family")
            }
            logger.info("User belongs to family: $familyId")

            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("Family not found with ID: $familyId")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name} with ${family.membersIds.size} members")

            val members = family.membersIds.mapNotNull { userRepository.findById(it).orElse(null) }
            logger.info("Retrieved ${members.size} family members")

            val response = mapOf(
                "family" to family,
                "members" to members
            )

            logger.info("Family details retrieved successfully for user: $currentUserId")
            ResponseEntity.ok(response)
        } catch (ex: Exception) {
            logger.error("Exception in getFamilyDetails for user: ${authUtil.getCurrentUserId()}", ex)
            ApiResponseUtil.internalServerError("An error occurred while getting family details")
        }
    }

    @PostMapping("/invite")
    fun inviteMember(
        @RequestParam invitedMemberEmail: String
    ): ResponseEntity<*> {
        logger.info("Inviting member with email: $invitedMemberEmail")
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $currentUserId")

            val headUser = userRepository.findById(currentUserId).orElse(null)
            if (headUser == null) {
                logger.warn("User not found with ID: $currentUserId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("Head user found: ${headUser.email}")

            val familyId = headUser.familyId
            if (familyId == null) {
                logger.warn("User $currentUserId does not belong to any family")
                return ApiResponseUtil.badRequest("User does not belong to a family")
            }
            logger.info("User belongs to family: $familyId")

            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("Family not found with ID: $familyId")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name}")

            if (family.headId != currentUserId) {
                logger.warn("User $currentUserId is not the family head. Head ID: ${family.headId}")
                return ApiResponseUtil.forbidden("Only the family head can invite members")
            }

            val invitedUser = userRepository.findAll().find { it.email == invitedMemberEmail }
            if (invitedUser == null) {
                logger.warn("Invited user not found with email: $invitedMemberEmail")
                return ApiResponseUtil.notFound("Invited user not found")
            }
            logger.info("Invited user found: ${invitedUser.id}")

            if (invitedUser.familyId != null) {
                logger.warn("Invited user $invitedMemberEmail already belongs to family: ${invitedUser.familyId}")
                return ApiResponseUtil.conflict("Invited user already belongs to a family")
            }

            if (family.pendingMemberEmails.contains(invitedMemberEmail)) {
                logger.warn("User $invitedMemberEmail already has pending invitation")
                return ApiResponseUtil.conflict("User already invited and pending")
            }

            val updatedFamily = family.copy(
                pendingMemberEmails = (family.pendingMemberEmails + invitedMemberEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            logger.info("Family updated with pending invitation for: $invitedMemberEmail")

            try {
                pushNotificationService.sendNotification(
                    invitedUser.fcmToken,
                    "Family Invitation",
                    "You have been invited to join the family '${family.name}' by ${headUser.name}. Please accept the invitation to join."
                )
                logger.info("Invitation notification sent to: $invitedMemberEmail")
            } catch (ex: Exception) {
                logger.error("Failed to send invitation notification to: $invitedMemberEmail", ex)
            }

            logger.info("Member invitation completed successfully for: $invitedMemberEmail")
            ResponseEntity.ok(mapOf("message" to "Invitation sent to $invitedMemberEmail and is pending acceptance"))
        } catch (ex: Exception) {
            logger.error("Exception in inviteMember for email: $invitedMemberEmail", ex)
            ApiResponseUtil.internalServerError("An error occurred while inviting member")
        }
    }

    @PostMapping("/request-join")
    fun requestToJoinFamily(
        @RequestParam aliasName: String
    ): ResponseEntity<*> {
        logger.info("User requesting to join family with alias: $aliasName")
        return try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $userId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            if (user.familyId != null) {
                logger.warn("User $userId already belongs to family: ${user.familyId}")
                return ApiResponseUtil.conflict("User already belongs to a family")
            }

            val family = familyRepository.findAll().find { it.aliasName == aliasName }
            if (family == null) {
                logger.warn("Family not found with alias: $aliasName")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name} (ID: ${family.familyId})")

            if (family.membersIds.size >= family.maxSize) {
                logger.warn("Family ${family.familyId} is full (${family.membersIds.size}/${family.maxSize})")
                return ApiResponseUtil.conflict("Family is full")
            }

            if (family.pendingJoinRequests.contains(user.email)) {
                logger.warn("User ${user.email} already has pending join request for family ${family.familyId}")
                return ApiResponseUtil.conflict("You have already requested to join this family")
            }

            val updatedFamily = family.copy(
                pendingJoinRequests = (family.pendingJoinRequests + user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            logger.info("Family ${family.familyId} updated with join request from: ${user.email}")

            // Notify the family head
            val headUser = userRepository.findById(family.headId).orElse(null)
            if (headUser != null) {
                logger.info("Notifying family head: ${family.headId}")
                try {
                    pushNotificationService.sendNotification(
                        headUser.fcmToken,
                        "Join Request",
                        "${user.name ?: user.email} (${user.email}) has requested to join your family '${family.name}'."
                    )
                    logger.info("Join request notification sent to family head: ${family.headId}")
                } catch (ex: Exception) {
                    logger.error("Failed to send join request notification to family head: ${family.headId}", ex)
                }

                // Save notification for the family head
                val notification = Notification(
                    id = UUID.randomUUID().toString(),
                    title = "Join Request",
                    message = "${user.name ?: user.email} (${user.email}) has requested to join your family '${family.name}'.",
                    time = System.currentTimeMillis(),
                    read = false,
                    familyId = family.familyId,
                    senderName = user.name ?: user.email,
                    senderId = user.id,
                    actionable = false,
                    createdAt = System.currentTimeMillis(),
                    type = NotificationType.FAMILY_JOIN
                )
                notificationRepository.save(notification)
                logger.info("Join request notification saved for family head: ${family.headId}")
            }

            logger.info("Join request completed successfully for user: $userId to family: ${family.familyId}")
            ResponseEntity.ok(mapOf("message" to "Join request sent to family head"))
        } catch (ex: Exception) {
            logger.error("Exception in requestToJoinFamily for user: ${authUtil.getCurrentUserId()}, alias: $aliasName", ex)
            ApiResponseUtil.internalServerError("An error occurred while requesting to join family")
        }
    }

    @PostMapping("/accept-join-request")
    fun acceptJoinRequest(
        @RequestParam requesterEmail: String
    ): ResponseEntity<*> {
        logger.info("Accepting join request for: $requesterEmail")
        return try {
            val headId = authUtil.getCurrentUserId()
            logger.info("Family head ID: $headId")

            val headUser = userRepository.findById(headId).orElse(null)
            if (headUser == null) {
                logger.warn("Head user not found with ID: $headId")
                return ApiResponseUtil.notFound("Head user not found")
            }
            logger.info("Head user found: ${headUser.email}")

            val familyId = headUser.familyId
            if (familyId == null) {
                logger.warn("Head user $headId does not belong to any family")
                return ApiResponseUtil.badRequest("User does not belong to a family")
            }
            logger.info("Head user belongs to family: $familyId")

            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("Family not found with ID: $familyId")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name}")

            if (family.headId != headId) {
                logger.warn("User $headId is not the family head. Head ID: ${family.headId}")
                return ApiResponseUtil.forbidden("Only the family head can accept join requests")
            }

            if (!family.pendingJoinRequests.contains(requesterEmail)) {
                logger.warn("No pending join request found for: $requesterEmail")
                return ApiResponseUtil.notFound("No such join request pending")
            }
            logger.info("Pending join request found for: $requesterEmail")

            val userToAdd = userRepository.findAll().find { it.email == requesterEmail }
            if (userToAdd == null) {
                logger.warn("User to add not found with email: $requesterEmail")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User to add found: ${userToAdd.id}")

            if (userToAdd.familyId != null) {
                logger.warn("User $requesterEmail already belongs to family: ${userToAdd.familyId}")
                return ApiResponseUtil.conflict("User already belongs to a family")
            }

            if (family.membersIds.size >= family.maxSize) {
                logger.warn("Family ${family.familyId} is full (${family.membersIds.size}/${family.maxSize})")
                return ApiResponseUtil.conflict("Family is full")
            }

            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userToAdd.id).toMutableList(),
                pendingJoinRequests = (family.pendingJoinRequests - requesterEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            logger.info("Family ${family.familyId} updated with new member: ${userToAdd.id}")

            val updatedUser = userToAdd.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            logger.info("User ${userToAdd.id} updated with family ID: ${family.familyId}")

            // Notify the user
            try {
                pushNotificationService.sendNotification(
                    userToAdd.fcmToken,
                    "Join Request Accepted",
                    "Your request to join the family '${family.name}' has been accepted."
                )
                logger.info("Join request acceptance notification sent to: $requesterEmail")
            } catch (ex: Exception) {
                logger.error("Failed to send join request acceptance notification to: $requesterEmail", ex)
            }

            // Save notification for the user who requested to join
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = "Join Request Accepted",
                message = "Your request to join the family '${family.name}' has been accepted.",
                time = System.currentTimeMillis(),
                read = false,
                familyId = family.familyId,
                senderName = headUser.name ?: headUser.email,
                senderId = headUser.id,
                actionable = false,
                createdAt = System.currentTimeMillis(),
                type = NotificationType.FAMILY_JOIN
            )
            notificationRepository.save(notification)
            logger.info("Join request acceptance notification saved for: $requesterEmail")

            logger.info("Join request acceptance completed successfully for: $requesterEmail")
            ResponseEntity.ok(mapOf("message" to "User added to family and notified"))
        } catch (ex: Exception) {
            logger.error("Exception in acceptJoinRequest for requester: $requesterEmail", ex)
            ApiResponseUtil.internalServerError("An error occurred while accepting join request")
        }
    }

    @PostMapping("/accept-invitation")
    fun acceptFamilyInvitation(
        @RequestParam aliasName: String
    ): ResponseEntity<*> {
        logger.info("Accepting family invitation for alias: $aliasName")
        return try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $userId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            if (user.familyId != null) {
                logger.warn("User $userId already belongs to family: ${user.familyId}")
                return ApiResponseUtil.conflict("User already belongs to a family")
            }

            val family = familyRepository.findAll().find { it.aliasName == aliasName }
            if (family == null) {
                logger.warn("Family not found with alias: $aliasName")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name} (ID: ${family.familyId})")

            if (!family.pendingMemberEmails.contains(user.email)) {
                logger.warn("No invitation found for user: ${user.email}")
                return ApiResponseUtil.notFound("No invitation found for this user")
            }
            logger.info("Invitation found for user: ${user.email}")

            if (family.membersIds.size >= family.maxSize) {
                logger.warn("Family ${family.familyId} is full (${family.membersIds.size}/${family.maxSize})")
                return ApiResponseUtil.conflict("Family is full")
            }

            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userId).toMutableList(),
                pendingMemberEmails = (family.pendingMemberEmails - user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            logger.info("Family ${family.familyId} updated with new member: $userId")

            val updatedUser = user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            logger.info("User $userId updated with family ID: ${family.familyId}")

            // Notify the head
            val headUser = userRepository.findById(family.headId).orElse(null)
            if (headUser != null) {
                logger.info("Notifying family head: ${family.headId}")
                try {
                    pushNotificationService.sendNotification(
                        headUser.fcmToken,
                        "Invitation Accepted",
                        "${user.name ?: user.email} (${user.email}) has accepted your invitation to join the family '${family.name}'."
                    )
                    logger.info("Invitation acceptance notification sent to family head: ${family.headId}")
                } catch (ex: Exception) {
                    logger.error("Failed to send invitation acceptance notification to family head: ${family.headId}", ex)
                }

                // Save notification for the head user
                val notification = Notification(
                    id = UUID.randomUUID().toString(),
                    title = "Invitation Accepted",
                    message = "${user.name ?: user.email} (${user.email}) has accepted your invitation to join the family '${family.name}'.",
                    time = System.currentTimeMillis(),
                    read = false,
                    familyId = family.familyId,
                    senderName = user.name ?: user.email,
                    senderId = user.id,
                    actionable = false,
                    createdAt = System.currentTimeMillis(),
                    type = NotificationType.FAMILY_JOIN
                )
                notificationRepository.save(notification)
                logger.info("Invitation acceptance notification saved for family head: ${family.headId}")
            }

            logger.info("Family invitation acceptance completed successfully for user: $userId")
            ResponseEntity.ok(mapOf("message" to "Joined family and head notified"))
        } catch (ex: Exception) {
            logger.error("Exception in acceptFamilyInvitation for user: ${authUtil.getCurrentUserId()}, alias: $aliasName", ex)
            ApiResponseUtil.internalServerError("An error occurred while accepting invitation")
        }
    }

    @PostMapping("/profile-photo")
    fun uploadFamilyProfilePhoto(
        @RequestParam photo: MultipartFile
    ): ResponseEntity<*> {
        logger.info("Uploading family profile photo: ${photo.originalFilename}")
        return try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $userId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            val familyId = user.familyId
            if (familyId == null) {
                logger.warn("User $userId does not belong to any family")
                return ApiResponseUtil.badRequest("User does not belong to a family")
            }
            logger.info("User belongs to family: $familyId")

            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("Family not found with ID: $familyId")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name}")

            // For demo: just use original filename as URL (replace with real storage in production)
            val photoUrl = "/uploads/family/${family.familyId}/${photo.originalFilename}"
            logger.info("Generated photo URL: $photoUrl")

            val updatedFamily = family.copy(profilePhotoUrl = photoUrl, updatedAt = System.currentTimeMillis())
            familyRepository.save(updatedFamily)
            logger.info("Family profile photo updated successfully for family: $familyId")

            ResponseEntity.ok(mapOf("profilePhotoUrl" to photoUrl))
        } catch (ex: Exception) {
            logger.error("Exception in uploadFamilyProfilePhoto for user: ${authUtil.getCurrentUserId()}", ex)
            ApiResponseUtil.internalServerError("An error occurred while uploading profile photo")
        }
    }

    @PostMapping("/add-members")
    fun addMembersToFamily(
        @RequestParam memberIds: List<String>
    ): ResponseEntity<*> {
        logger.info("Adding members to family: $memberIds")
        return try {
            val userId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $userId")

            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                logger.warn("User not found with ID: $userId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("User found: ${user.email}")

            val familyId = user.familyId
            if (familyId == null) {
                logger.warn("User $userId does not belong to any family")
                return ApiResponseUtil.badRequest("User does not belong to a family")
            }
            logger.info("User belongs to family: $familyId")

            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("Family not found with ID: $familyId")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name}")

            // Only allow valid expense users
            val validMembers = memberIds.filter { userRepository.existsById(it) }
            logger.info("Valid members found: ${validMembers.size} out of ${memberIds.size}")

            val updatedFamily = family.copy(
                membersIds = validMembers.toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            logger.info("Family members updated successfully for family: $familyId")

            ResponseEntity.ok(updatedFamily)
        } catch (ex: Exception) {
            logger.error("Exception in addMembersToFamily for user: ${authUtil.getCurrentUserId()}, memberIds: $memberIds", ex)
            ApiResponseUtil.internalServerError("An error occurred while adding members")
        }
    }

    @PostMapping("/resend-invitation")
    fun resendInvitation(
        @RequestParam invitedMemberEmail: String
    ): ResponseEntity<*> {
        logger.info("Resending invitation to member with email: $invitedMemberEmail")
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            logger.info("Current user ID: $currentUserId")

            val headUser = userRepository.findById(currentUserId).orElse(null)
            if (headUser == null) {
                logger.warn("User not found with ID: $currentUserId")
                return ApiResponseUtil.notFound("User not found")
            }
            logger.info("Head user found: ${headUser.email}")

            val familyId = headUser.familyId
            if (familyId == null) {
                logger.warn("User $currentUserId does not belong to any family")
                return ApiResponseUtil.badRequest("User does not belong to a family")
            }
            logger.info("User belongs to family: $familyId")

            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("Family not found with ID: $familyId")
                return ApiResponseUtil.notFound("Family not found")
            }
            logger.info("Family found: ${family.name}")

            if (family.headId != currentUserId) {
                logger.warn("User $currentUserId is not the family head. Head ID: ${family.headId}")
                return ApiResponseUtil.forbidden("Only the family head can resend invitations")
            }

            if (!family.pendingMemberEmails.contains(invitedMemberEmail)) {
                logger.warn("No pending invitation found for email: $invitedMemberEmail")
                return ApiResponseUtil.notFound("No pending invitation found for this email")
            }
            logger.info("Pending invitation found for: $invitedMemberEmail")

            val invitedUser = userRepository.findAll().find { it.email == invitedMemberEmail }
            if (invitedUser == null) {
                logger.warn("Invited user not found with email: $invitedMemberEmail")
                return ApiResponseUtil.notFound("Invited user not found")
            }
            logger.info("Invited user found: ${invitedUser.id}")

            if (invitedUser.familyId != null) {
                logger.warn("Invited user $invitedMemberEmail already belongs to family: ${invitedUser.familyId}")
                return ApiResponseUtil.conflict("Invited user already belongs to a family")
            }

            try {
                pushNotificationService.sendNotification(
                    invitedUser.fcmToken,
                    "Family Invitation (Reminder)",
                    "Reminder: You have been invited to join the family '${family.name}' by ${headUser.name}. Please accept the invitation to join."
                )
                logger.info("Invitation reminder notification sent to: $invitedMemberEmail")
            } catch (ex: Exception) {
                logger.error("Failed to send invitation reminder notification to: $invitedMemberEmail", ex)
                return ApiResponseUtil.internalServerError("Failed to send invitation reminder")
            }

            // Save notification in database
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                title = "Family Invitation (Reminder)",
                message = "Reminder: You have been invited to join the family '${family.name}' by ${headUser.name}. Please accept the invitation to join.",
                time = System.currentTimeMillis(),
                read = false,
                familyId = family.familyId,
                senderName = headUser.name ?: headUser.email,
                senderId = headUser.id,
                actionable = true,
                createdAt = System.currentTimeMillis(),
                type = NotificationType.FAMILY_INVITE
            )
            notificationRepository.save(notification)
            logger.info("Invitation reminder notification saved for: $invitedMemberEmail")

            logger.info("Invitation resent successfully to: $invitedMemberEmail")
            ResponseEntity.ok(mapOf("message" to "Invitation reminder sent to $invitedMemberEmail"))
        } catch (ex: Exception) {
            logger.error("Exception in resendInvitation for email: $invitedMemberEmail", ex)
            ApiResponseUtil.internalServerError("An error occurred while resending invitation")
        }
    }
}
