package com.example.expensetracker.service

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
        val keyBytes = Decoders.BASE64.decode(secretKey)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
