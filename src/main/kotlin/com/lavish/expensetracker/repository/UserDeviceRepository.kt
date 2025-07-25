package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface UserDeviceRepository : JpaRepository<UserDevice, String> {

    fun findByUserIdAndIsActive(userId: String, isActive: Boolean = true): List<UserDevice>

    fun findByFcmToken(fcmToken: String): UserDevice?

    fun findByUserIdAndFcmToken(userId: String, fcmToken: String): UserDevice?

    @Query("SELECT ud.fcmToken FROM UserDevice ud WHERE ud.userId = :userId AND ud.isActive = true")
    fun findActiveFcmTokensByUserId(@Param("userId") userId: String): List<String>

    @Modifying
    @Transactional
    @Query("UPDATE UserDevice ud SET ud.isActive = false WHERE ud.userId = :userId AND ud.fcmToken = :fcmToken")
    fun deactivateDeviceByToken(@Param("userId") userId: String, @Param("fcmToken") fcmToken: String): Int

    @Modifying
    @Transactional
    @Query("UPDATE UserDevice ud SET ud.isActive = false WHERE ud.userId = :userId")
    fun deactivateAllUserDevices(@Param("userId") userId: String): Int

    @Modifying
    @Transactional
    @Query("UPDATE UserDevice ud SET ud.userId = :newUserId, ud.deviceName = :deviceName, ud.deviceType = :deviceType, ud.updatedAt = :updatedAt, ud.isActive = true WHERE ud.fcmToken = :fcmToken")
    fun transferDeviceToUser(
        @Param("fcmToken") fcmToken: String,
        @Param("newUserId") newUserId: String,
        @Param("deviceName") deviceName: String?,
        @Param("deviceType") deviceType: String?,
        @Param("updatedAt") updatedAt: Long
    ): Int
}
