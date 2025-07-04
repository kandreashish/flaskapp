package com.example.expensetracker.service

import com.example.expensetracker.model.RefreshToken
import com.example.expensetracker.repository.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.*

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository
) {

    private val logger = LoggerFactory.getLogger(RefreshTokenService::class.java)

    @Value("\${jwt.refresh-expiration:604800000}") // 7 days default
    private var refreshTokenExpiration: Long = 604800000

    private val secureRandom = SecureRandom()

    fun generateRefreshToken(userId: String): String {
        // Revoke all existing refresh tokens for this user
        refreshTokenRepository.revokeAllUserTokens(userId)

        // Generate a secure random token
        val tokenBytes = ByteArray(32)
        secureRandom.nextBytes(tokenBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)

        // Create and save refresh token
        val refreshToken = RefreshToken(
            token = token,
            userId = userId,
            expiresAt = System.currentTimeMillis() + refreshTokenExpiration
        )

        refreshTokenRepository.save(refreshToken)
        logger.info("Generated new refresh token for user: $userId")

        return token
    }

    fun validateRefreshToken(token: String): String? {
        val refreshToken = refreshTokenRepository.findByToken(token)

        if (refreshToken == null) {
            logger.warn("Refresh token not found: $token")
            return null
        }

        if (refreshToken.isRevoked) {
            logger.warn("Refresh token is revoked: $token")
            return null
        }

        if (refreshToken.expiresAt < System.currentTimeMillis()) {
            logger.warn("Refresh token is expired: $token")
            // Mark as revoked and remove
            refreshToken.isRevoked = true
            refreshTokenRepository.save(refreshToken)
            return null
        }

        return refreshToken.userId
    }

    @Transactional
    fun revokeRefreshToken(token: String): Boolean {
        val refreshToken = refreshTokenRepository.findByToken(token)
        if (refreshToken != null) {
            refreshToken.isRevoked = true
            refreshTokenRepository.save(refreshToken)
            logger.info("Revoked refresh token: $token")
            return true
        }
        return false
    }

    @Transactional
    fun revokeAllUserTokens(userId: String) {
        refreshTokenRepository.revokeAllUserTokens(userId)
        logger.info("Revoked all refresh tokens for user: $userId")
    }

    @Transactional
    fun cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(System.currentTimeMillis())
        logger.info("Cleaned up expired refresh tokens")
    }
}
