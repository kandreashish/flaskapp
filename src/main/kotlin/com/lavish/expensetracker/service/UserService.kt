package com.lavish.expensetracker.service

import com.lavish.expensetracker.controller.UserController
import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.repository.ExpenseUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: ExpenseUserRepository,
    private val userDeviceService: UserDeviceService,
    private val fileStorageService: FileStorageService
) {

    fun findByFirebaseUid(firebaseUid: String): ExpenseUser? {
        return userRepository.findByFirebaseUid(firebaseUid)
    }

    fun findById(id: String): ExpenseUser? {
        return userRepository.findById(id).orElse(null)
    }

    @Transactional
    fun updateFcmToken(userId: String, fcmToken: String, deviceName: String? = null, deviceType: String? = null): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false

        // Add or update device token in the new UserDevice table
        userDeviceService.addOrUpdateDevice(userId, fcmToken, deviceName, deviceType)

        // Keep the legacy fcmToken field updated for backward compatibility
        val updatedUser = user.copy(
            fcmToken = fcmToken,
            updatedAt = System.currentTimeMillis()
        )
        userRepository.save(updatedUser)
        return true
    }

    fun getAllFcmTokens(userId: String): List<String> {
        return userDeviceService.getActiveDeviceTokens(userId)
    }

    fun getFamilyMembersFcmTokens(familyId: String): List<ExpenseUser> {
        return userRepository.findByFamilyId(familyId)
    }

    @Transactional
    fun removeDevice(userId: String, fcmToken: String): Boolean {
        return userDeviceService.deactivateDevice(userId, fcmToken)
    }

    @Transactional
    fun logoutAllDevices(userId: String): Boolean {
        return userDeviceService.deactivateAllUserDevices(userId)
    }

    fun userExists(userId: String): Boolean {
        return userRepository.existsById(userId)
    }

    @Transactional
    fun updateProfile(userId: String, updateRequest: UserController.UpdateProfileRequest): ExpenseUser? {
        val existingUser = userRepository.findById(userId).orElse(null) ?: return null

        val updatedUser = existingUser.copy(
            name = updateRequest.name ?: existingUser.name,
            profilePic = updateRequest.profilePic ?: existingUser.profilePic,
            currencyPreference = updateRequest.currencyPreference ?: existingUser.currencyPreference,
            updatedAt = System.currentTimeMillis()
        )

        return userRepository.save(updatedUser)
    }

    @Transactional
    fun updateProfilePicture(userId: String, profilePicUrl: String): ExpenseUser? {
        val existingUser = userRepository.findById(userId).orElse(null) ?: return null

        existingUser.profilePic?.let { oldPicUrl ->
            if (oldPicUrl.contains("/api/files/profile-pics/") && oldPicUrl != profilePicUrl) {
                fileStorageService.deleteProfilePicture(oldPicUrl)
            }
        }

        val updatedUser = existingUser.copy(
            profilePic = profilePicUrl,
            updatedAt = System.currentTimeMillis()
        )

        return userRepository.save(updatedUser)
    }
}
