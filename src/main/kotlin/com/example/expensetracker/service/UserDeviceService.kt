package com.example.expensetracker.service

import com.example.expensetracker.model.UserDevice
import com.example.expensetracker.repository.UserDeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository
) {

    @Transactional
    fun addOrUpdateDevice(
        userId: String,
        fcmToken: String,
        deviceName: String? = null,
        deviceType: String? = null
    ): UserDevice {
        // First, check if this exact user-token combination exists
        val existingUserDevice = userDeviceRepository.findByUserIdAndFcmToken(userId, fcmToken)

        if (existingUserDevice != null) {
            // Update existing device for same user
            val updatedDevice = existingUserDevice.copy(
                deviceName = deviceName ?: existingUserDevice.deviceName,
                deviceType = deviceType ?: existingUserDevice.deviceType,
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
            return userDeviceRepository.save(updatedDevice)
        }

        // Check if this FCM token is already associated with any user (active or inactive)
        val existingTokenDevice = userDeviceRepository.findByFcmToken(fcmToken)

        if (existingTokenDevice != null) {
            // Transfer the existing device record to the new user instead of creating a new one
            val updatedAt = System.currentTimeMillis()
            val rowsUpdated = userDeviceRepository.transferDeviceToUser(
                fcmToken = fcmToken,
                newUserId = userId,
                deviceName = deviceName,
                deviceType = deviceType,
                updatedAt = updatedAt
            )

            if (rowsUpdated > 0) {
                // Return the updated device record
                return userDeviceRepository.findByFcmToken(fcmToken)!!
            }
        }

        // No existing device with this token, create new one
        val newDevice = UserDevice(
            userId = userId,
            fcmToken = fcmToken,
            deviceName = deviceName,
            deviceType = deviceType
        )
        return userDeviceRepository.save(newDevice)
    }

    fun getActiveDeviceTokens(userId: String): List<String> {
        return userDeviceRepository.findActiveFcmTokensByUserId(userId)
    }

    fun getActiveDevices(userId: String): List<UserDevice> {
        return userDeviceRepository.findByUserIdAndIsActive(userId, true)
    }

    @Transactional
    fun deactivateDevice(userId: String, fcmToken: String): Boolean {
        val rowsAffected = userDeviceRepository.deactivateDeviceByToken(userId, fcmToken)
        return rowsAffected > 0
    }

    @Transactional
    fun deactivateAllUserDevices(userId: String): Boolean {
        val rowsAffected = userDeviceRepository.deactivateAllUserDevices(userId)
        return rowsAffected > 0
    }

    fun removeInvalidTokens(invalidTokens: List<String>) {
        invalidTokens.forEach { token ->
            val device = userDeviceRepository.findByFcmToken(token)
            device?.let {
                userDeviceRepository.deactivateDeviceByToken(it.userId, token)
            }
        }
    }
}
