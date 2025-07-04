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
        // Check if device already exists
        val existingDevice = userDeviceRepository.findByUserIdAndFcmToken(userId, fcmToken)

        return if (existingDevice != null) {
            // Update existing device
            val updatedDevice = existingDevice.copy(
                deviceName = deviceName ?: existingDevice.deviceName,
                deviceType = deviceType ?: existingDevice.deviceType,
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
            userDeviceRepository.save(updatedDevice)
        } else {
            // Create new device
            val newDevice = UserDevice(
                userId = userId,
                fcmToken = fcmToken,
                deviceName = deviceName,
                deviceType = deviceType
            )
            userDeviceRepository.save(newDevice)
        }
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
