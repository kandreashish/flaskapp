package com.lavish.expensetracker.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.Key
import java.util.Date

@Service
class JwtService {

    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    @Value("\${jwt.secret}")
    private lateinit var secretKey: String

    @Value("\${jwt.expiration}")
    private var jwtExpiration: Long = 0

    fun generateToken(userId: String): String {
        return Jwts.builder()
            .subject(userId)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(getSigningKey())
            .compact()
    }

    fun extractUserId(token: String): String? {
        return try {
            extractClaim(token) { it.subject }
        } catch (e: Exception) {
            logger.error("Error extracting user ID from token: ${e.message}", e)
            null
        }
    }

    fun isTokenValid(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token)
            !isTokenExpired(claims)
        } catch (e: Exception) {
            logger.error("Error validating token: ${e.message}", e)
            false
        }
    }

    fun getTokenExpirationTime(token: String): Long? {
        return try {
            val claims = extractAllClaims(token)
            claims.expiration.time - System.currentTimeMillis()
        } catch (e: Exception) {
            logger.error("Error getting token expiration time: ${e.message}", e)
            null
        }
    }

    private fun isTokenExpired(claims: Claims): Boolean {
        return claims.expiration.before(Date())
    }

    private fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
    }

    private fun getSigningKey(): javax.crypto.SecretKey {
        logger.info("JWT Secret Key length: ${secretKey.length}, value preview: ${secretKey.take(10)}...")

        // Handle both hexadecimal and base64 encoded keys
        val keyBytes = try {
            // First try as hexadecimal (if it contains only hex characters)
            if (secretKey.matches(Regex("^[0-9a-fA-F]+$"))) {
                // Convert hex string to bytes
                secretKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else {
                // Fallback to base64 decoding
                Decoders.BASE64.decode(secretKey)
            }
        } catch (e: Exception) {
            logger.error("Error decoding JWT secret key: ${e.message}", e)
            throw IllegalArgumentException("Invalid JWT secret key format", e)
        }

        if (keyBytes.isEmpty()) {
            logger.error("JWT secret key is empty! secretKey value: '$secretKey'")
            throw IllegalArgumentException("JWT secret key cannot be empty. Check that JWT_SECRET environment variable is set.")
        }

        logger.info("Successfully created JWT signing key with ${keyBytes.size * 8} bits")
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
