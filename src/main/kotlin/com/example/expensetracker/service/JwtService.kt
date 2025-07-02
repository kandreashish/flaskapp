package com.example.expensetracker.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.SecretKey
import java.util.*

@Service
class JwtService {

    @Value("\${jwt.secret:mySecretKey123456789012345678901234567890}")
    private lateinit var jwtSecret: String

    @Value("\${jwt.expiration:86400000}")
    private val jwtExpiration: Long = 86400000 // 24 hours

    private fun getSigningKey(): SecretKey {
        return Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    fun generateToken(userId: String, email: String): String {
        return Jwts.builder()
            .claim("userId", userId)
            .claim("email", email)
            .subject(userId)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(getSigningKey())
            .compact()
    }

    fun extractUserId(token: String): String {
        return extractAllClaims(token).subject
    }

    fun extractEmail(token: String): String {
        return extractAllClaims(token)["email"] as String
    }

    fun isTokenValid(token: String): Boolean {
        return try {
            extractAllClaims(token)
            !isTokenExpired(token)
        } catch (e: Exception) {
            false
        }
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractAllClaims(token).expiration.before(Date())
    }

    fun getExpirationTime(): Long = jwtExpiration
}
