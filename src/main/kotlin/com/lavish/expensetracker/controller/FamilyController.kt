package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.model.Family
import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.NotificationType
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.AuthUtil
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
    @Autowired private val pushNotificationService: PushNotificationService,
    private val authUtil: AuthUtil,
    private val notificationRepository: NotificationRepository,
) {
    private val logger = LoggerFactory.getLogger(FamilyController::class.java)
    @PostMapping("/create")
    fun createFamily(
        @RequestParam familyName: String
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ResponseEntity.badRequest().body("User not found")
            if (user.familyId != null) {
                return ResponseEntity.badRequest().body("User already belongs to a family")
            }
            val familyId = UUID.randomUUID().toString()
            val aliasName = generateUniqueAliasName()
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
            val updatedUser = user.copy(familyId = familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            ResponseEntity.ok(family)
        } catch (ex: Exception) {
            logger.error("Exception in createFamily: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while creating family.")
        }
    }

    fun generateUniqueAliasName(): String {
        return try {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            var alias: String
            do {
                alias = (1..6)
                    .map { chars.random() }
                    .joinToString("")
            } while (familyRepository.findAll().any { it.aliasName == alias })
            alias
        } catch (ex: Exception) {
            logger.error("Exception in generateUniqueAliasName: ", ex)
            throw ex
        }
    }

    @PostMapping("/join")
    fun joinFamily(
        @RequestParam aliasName: String
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ResponseEntity.badRequest().body("User not found")
            if (user.familyId != null) {
                return ResponseEntity.badRequest().body("User already belongs to a family")
            }
            val family = familyRepository.findAll().find { it.aliasName == aliasName }
                ?: return ResponseEntity.badRequest().body("Family not found")
            if (family.membersIds.size >= family.maxSize) {
                return ResponseEntity.badRequest().body("Family is full")
            }
            val updatedFamily = family.copy(
                membersIds = (family.membersIds.toMutableList() + userId).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            val updatedUser = user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            ResponseEntity.ok(updatedFamily)
        } catch (ex: Exception) {
            logger.error("Exception in joinFamily: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while joining family.")
        }
    }

    @PostMapping("/leave")
    fun leaveFamily(
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ResponseEntity.badRequest().body("User not found")
            val familyId = user.familyId ?: return ResponseEntity.badRequest().body("User does not belong to a family")
            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ResponseEntity.badRequest().body("Family not found")
            if (!family.membersIds.contains(userId)) {
                return ResponseEntity.badRequest().body("User is not a member of this family")
            }
            val newMembers = family.membersIds.toMutableList().apply { remove(userId) }
            if (newMembers.isEmpty()) {
                familyRepository.delete(family)
            } else {
                val newHeadId = if (family.headId == userId) newMembers.first() else family.headId
                val updatedFamily = family.copy(
                    membersIds = newMembers,
                    headId = newHeadId,
                    updatedAt = System.currentTimeMillis()
                )
                familyRepository.save(updatedFamily)
            }
            val updatedUser = user.copy(familyId = null, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            //send push notification to the family head
            if (family.headId != userId) {
                val headUser = userRepository.findById(family.headId).orElse(null)
                    ?: return ResponseEntity.badRequest().body("Family head not found")
                pushNotificationService.sendNotification(
                    headUser.fcmToken,
                    "Family Member Left",
                    "${user.name ?: user.email} has left the family '${family.name}'."
                )
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
            }
            ResponseEntity.ok("Left family successfully")
        } catch (ex: Exception) {
            logger.error("Exception in leaveFamily: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while leaving family.")
        }
    }

    @GetMapping("/details")
    fun getFamilyDetails(
    ): ResponseEntity<Any> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            val user = userRepository.findById(currentUserId).orElse(null)
                ?: return ResponseEntity.badRequest().body("User not found")
            val familyId = user.familyId ?: return ResponseEntity.badRequest().body("User does not belong to a family")
            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ResponseEntity.badRequest().body("Family not found")
            ResponseEntity.ok(family)
        } catch (ex: Exception) {
            logger.error("Exception in getFamilyDetails: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while getting family details.")
        }
    }

    @PostMapping("/invite")
    fun inviteMember(
        @RequestParam invitedMemberEmail: String
    ): ResponseEntity<Any> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(currentUserId).orElse(null)
                ?: return ResponseEntity.badRequest().body("User not found")
            val familyId = headUser.familyId ?: return ResponseEntity.badRequest().body("User does not belong to a family")
            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ResponseEntity.badRequest().body("Family not found")
            if (family.headId != currentUserId) {
                return ResponseEntity.badRequest().body("Only the family head can invite members")
            }
            val invitedUser = userRepository.findAll().find { it.email == invitedMemberEmail }
                ?: return ResponseEntity.badRequest().body("Invited user not found")
            if (invitedUser.familyId != null) {
                return ResponseEntity.badRequest().body("Invited user already belongs to a family")
            }
            if (family.pendingMemberEmails.contains(invitedMemberEmail)) {
                return ResponseEntity.badRequest().body("User already invited and pending")
            }
            val updatedFamily = family.copy(
                pendingMemberEmails = (family.pendingMemberEmails + invitedMemberEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)

            pushNotificationService.sendNotification(
                invitedUser.fcmToken,
                "Family Invitation",
                "You have been invited to join the family '${family.name}' by ${headUser.name}. Please accept the invitation to join."
            )
            ResponseEntity.ok("Invitation sent to $invitedMemberEmail and is pending acceptance.")
        } catch (ex: Exception) {
            logger.error("Exception in inviteMember: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while inviting member.")
        }
    }

    @PostMapping("/request-join")
    fun requestToJoinFamily(
        @RequestParam aliasName: String
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ResponseEntity.badRequest().body("User not found")
            if (user.familyId != null) {
                return ResponseEntity.badRequest().body("User already belongs to a family")
            }
            val family = familyRepository.findAll().find { it.aliasName == aliasName }
                ?: return ResponseEntity.badRequest().body("Family not found")
            if (family.membersIds.size >= family.maxSize) {
                return ResponseEntity.badRequest().body("Family is full")
            }
            if (family.pendingJoinRequests.contains(user.email)) {
                return ResponseEntity.badRequest().body("You have already requested to join this family")
            }
            val updatedFamily = family.copy(
                pendingJoinRequests = (family.pendingJoinRequests + user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            // Notify the family head
            val headUser = userRepository.findById(family.headId).orElse(null)
            if (headUser != null) {
                pushNotificationService.sendNotification(
                    headUser.fcmToken,
                    "Join Request",
                    "${user.name ?: user.email} (${user.email}) has requested to join your family '${family.name}'."
                )
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
            }
            ResponseEntity.ok("Join request sent to family head.")
        } catch (ex: Exception) {
            logger.error("Exception in requestToJoinFamily: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while requesting to join family.")
        }
    }

    @PostMapping("/accept-join-request")
    fun acceptJoinRequest(
        @RequestParam requesterEmail: String
    ): ResponseEntity<Any> {
        return try {
            val headId = authUtil.getCurrentUserId()
            val headUser = userRepository.findById(headId).orElse(null)
                ?: return ResponseEntity.badRequest().body("Head user not found")
            val familyId = headUser.familyId ?: return ResponseEntity.badRequest().body("Head user does not belong to a family")
            val family = familyRepository.findById(familyId).orElse(null)
                ?: return ResponseEntity.badRequest().body("Family not found")
            if (family.headId != headId) {
                return ResponseEntity.badRequest().body("Only the family head can accept join requests")
            }
            if (!family.pendingJoinRequests.contains(requesterEmail)) {
                return ResponseEntity.badRequest().body("No such join request pending")
            }
            val userToAdd = userRepository.findAll().find { it.email == requesterEmail }
                ?: return ResponseEntity.badRequest().body("User not found")
            if (userToAdd.familyId != null) {
                return ResponseEntity.badRequest().body("User already belongs to a family")
            }
            if (family.membersIds.size >= family.maxSize) {
                return ResponseEntity.badRequest().body("Family is full")
            }
            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userToAdd.id).toMutableList(),
                pendingJoinRequests = (family.pendingJoinRequests - requesterEmail).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            val updatedUser = userToAdd.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            // Notify the user
            pushNotificationService.sendNotification(
                userToAdd.fcmToken,
                "Join Request Accepted",
                "Your request to join the family '${family.name}' has been accepted."
            )
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
            ResponseEntity.ok("User added to family and notified.")
        } catch (ex: Exception) {
            logger.error("Exception in acceptJoinRequest: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while accepting join request.")
        }
    }

    @PostMapping("/accept-invitation")
    fun acceptFamilyInvitation(
        @RequestParam aliasName: String
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val user = userRepository.findById(userId).orElse(null)
                ?: return ResponseEntity.badRequest().body("User not found")
            if (user.familyId != null) {
                return ResponseEntity.badRequest().body("User already belongs to a family")
            }
            val family = familyRepository.findAll().find { it.aliasName == aliasName }
                ?: return ResponseEntity.badRequest().body("Family not found")
            if (!family.pendingMemberEmails.contains(user.email)) {
                return ResponseEntity.badRequest().body("No invitation found for this user")
            }
            if (family.membersIds.size >= family.maxSize) {
                return ResponseEntity.badRequest().body("Family is full")
            }
            val updatedFamily = family.copy(
                membersIds = (family.membersIds + userId).toMutableList(),
                pendingMemberEmails = (family.pendingMemberEmails - user.email).toMutableList(),
                updatedAt = System.currentTimeMillis()
            )
            familyRepository.save(updatedFamily)
            val updatedUser = user.copy(familyId = family.familyId, updatedAt = System.currentTimeMillis())
            userRepository.save(updatedUser)
            // Notify the head
            val headUser = userRepository.findById(family.headId).orElse(null)
            if (headUser != null) {
                pushNotificationService.sendNotification(
                    headUser.fcmToken,
                    "Invitation Accepted",
                    "${user.name ?: user.email} (${user.email}) has accepted your invitation to join the family '${family.name}'."
                )
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
            }
            ResponseEntity.ok("Joined family and head notified.")
        } catch (ex: Exception) {
            logger.error("Exception in acceptFamilyInvitation: ", ex)
            ResponseEntity.internalServerError().body("An error occurred while accepting invitation.")
        }
    }
}
