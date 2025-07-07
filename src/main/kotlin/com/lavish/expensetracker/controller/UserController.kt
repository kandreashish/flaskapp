package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.UserDevice
import com.lavish.expensetracker.service.UserService
import com.lavish.expensetracker.service.UserDeviceService
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val userDeviceService: UserDeviceService,
    private val authUtil: AuthUtil
) {

    data class FcmTokenRequest(
        val fcmToken: String,
        val deviceName: String? = null,
        val deviceType: String? = null // "android", "ios", "web"
    )

    data class RemoveDeviceRequest(val fcmToken: String)

    @PostMapping("/fcm-token")
    fun updateFcmToken(@RequestBody request: FcmTokenRequest): ResponseEntity<String> {
        val currentUserId = authUtil.getCurrentUserId()
        val success = userService.updateFcmToken(
            currentUserId,
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
        val currentUserId = authUtil.getCurrentUserId()
        val devices = userDeviceService.getActiveDevices(currentUserId)
        return ResponseEntity.ok(devices)
    }

    @DeleteMapping("/device")
    fun removeDevice(@RequestBody request: RemoveDeviceRequest): ResponseEntity<String> {
        val currentUserId = authUtil.getCurrentUserId()
        val success = userService.removeDevice(currentUserId, request.fcmToken)

        return if (success) {
            ResponseEntity.ok("Device removed successfully")
        } else {
            ResponseEntity.badRequest().body("Failed to remove device or device not found")
        }
    }

    @DeleteMapping("/devices")
    fun logoutAllDevices(): ResponseEntity<String> {
        val currentUserId = authUtil.getCurrentUserId()
        val success = userService.logoutAllDevices(currentUserId)

        return if (success) {
            ResponseEntity.ok("All devices logged out successfully")
        } else {
            ResponseEntity.badRequest().body("Failed to logout all devices")
        }
    }
}
