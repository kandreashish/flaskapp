package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.model.UserDevice
import com.lavish.expensetracker.service.UserDeviceService
import com.lavish.expensetracker.service.UserService
import com.lavish.expensetracker.service.FileStorageService
import com.lavish.expensetracker.util.AuthUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val userDeviceService: UserDeviceService,
    private val fileStorageService: FileStorageService,
    private val authUtil: AuthUtil
) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)


    private fun getCurrentUserWithValidation(): ExpenseUser {
        val currentUserId = try {
            authUtil.getCurrentUserId()
        } catch (e: ResponseStatusException) {
            logger.warn("Authentication/Authorization failed: ${e.message}")
            throw when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authentication required. Please provide a valid JWT token."
                )

                HttpStatus.FORBIDDEN -> ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please re-authenticate."
                )

                else -> ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Authentication failed: ${e.reason}"
                )
            }
        }

        return userService.findById(currentUserId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ExpenseUser not found")
    }

    data class FcmTokenRequest(
        val fcmToken: String,
        val deviceName: String? = null,
        val deviceType: String? = null // "android", "ios", "web"
    )

    data class RemoveDeviceRequest(val fcmToken: String)

    data class UpdateProfileRequest(
        val name: String?,
        val profilePic: String?,
        val currencyPreference: String?
    )

    data class ProfileResponse(
        val id: String,
        val name: String?,
        val email: String,
        val aliasName: String,
        val profilePic: String?,
        val currencyPreference: String,
        val familyId: String?,
        val createdAt: Long,
        val updatedAt: Long
    )

    @PostMapping("/fcm-token")
    fun updateFcmToken(@RequestBody request: FcmTokenRequest): ResponseEntity<String> {
        val currentUser = getCurrentUserWithValidation()
        val success = userService.updateFcmToken(
            currentUser.id,
            request.fcmToken,
            request.deviceName,
            request.deviceType
        )

        return if (success) {
            ResponseEntity.ok("FCM token updated successfully for device")
        } else {
            ResponseEntity.badRequest().body("Failed to update FCM token")
        }
    }

    @GetMapping("/devices")
    fun getUserDevices(): ResponseEntity<List<UserDevice>> {
        val currentUser = getCurrentUserWithValidation()
        val devices = userDeviceService.getActiveDevices(currentUser.id)
        return ResponseEntity.ok(devices)
    }

    @DeleteMapping("/device")
    fun removeDevice(@RequestBody request: RemoveDeviceRequest): ResponseEntity<String> {
        val currentUserId = getCurrentUserWithValidation().id
        val success = userService.removeDevice(currentUserId, request.fcmToken)

        return if (success) {
            ResponseEntity.ok("Device removed successfully")
        } else {
            ResponseEntity.badRequest().body("Failed to remove device or device not found")
        }
    }

    @DeleteMapping("/devices")
    fun logoutAllDevices(): ResponseEntity<String> {
        val currentUserId = getCurrentUserWithValidation().id
        val success = userService.logoutAllDevices(currentUserId)

        return if (success) {
            ResponseEntity.ok("All devices logged out successfully")
        } else {
            ResponseEntity.badRequest().body("Failed to logout all devices")
        }
    }

    @PutMapping("/profile")
    fun updateProfile(@RequestBody request: UpdateProfileRequest): ResponseEntity<ProfileResponse> {
        val currentUserId = getCurrentUserWithValidation().id
        val updatedUser = userService.updateProfile(currentUserId, request)

        return if (updatedUser != null) {
            val response = ProfileResponse(
                id = updatedUser.id,
                name = updatedUser.name,
                email = updatedUser.email,
                aliasName = updatedUser.aliasName,
                profilePic = updatedUser.profilePic,
                currencyPreference = updatedUser.currencyPreference,
                familyId = updatedUser.familyId,
                createdAt = updatedUser.createdAt,
                updatedAt = updatedUser.updatedAt
            )
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/profile")
    fun getProfile(): ResponseEntity<ProfileResponse> {
        val currentUserId = authUtil.getCurrentUserId()
        val user = userService.findById(currentUserId)

        return if (user != null) {
            val response = ProfileResponse(
                id = user.id,
                name = user.name,
                email = user.email,
                aliasName = user.aliasName,
                profilePic = user.profilePic,
                currencyPreference = user.currencyPreference,
                familyId = user.familyId,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/profile-picture", consumes = ["multipart/form-data"])
    fun uploadProfilePicture(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, String>> {
        logger.debug("Received profile picture upload request. File empty: ${file.isEmpty}, Size: ${file.size}, Content-Type: ${file.contentType}")
        val currentUser = getCurrentUserWithValidation()

        return try {
            // Upload the file and get the URL
            val profilePicUrl = fileStorageService.uploadProfilePicture(file, currentUser.id)

            // Update the user's profile picture URL in the database
            val updatedUser = userService.updateProfilePicture(currentUser.id, profilePicUrl)

            if (updatedUser != null) {
                ResponseEntity.ok(mapOf(
                    "message" to "Profile picture uploaded successfully",
                    "profilePicUrl" to "http://192.168.1.79$profilePicUrl"
                ))
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "Failed to update profile picture in database"))
            }
        } catch (e: ResponseStatusException) {
            ResponseEntity.status(e.statusCode)
                .body(mapOf("error" to (e.reason ?: "Upload failed")))
        } catch (e: Exception) {
            logger.error("Error uploading profile picture for user ${currentUser.id}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to upload profile picture"))
        }
    }
}
