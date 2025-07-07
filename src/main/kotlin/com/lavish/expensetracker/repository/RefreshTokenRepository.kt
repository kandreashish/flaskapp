package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, String> {

    fun findByToken(token: String): RefreshToken?

    fun findByUserIdAndIsRevokedFalse(userId: String): List<RefreshToken>

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = :userId")
    fun revokeAllUserTokens(@Param("userId") userId: String)

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :currentTime")
    fun deleteExpiredTokens(@Param("currentTime") currentTime: Long)
}
