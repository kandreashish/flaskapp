package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.auth.*
import com.lavish.expensetracker.service.AuthService
import com.lavish.expensetracker.service.JwtService
import com.lavish.expensetracker.service.RefreshTokenService
import com.lavish.expensetracker.util.AuthUtil
import com.google.api.client.auth.oauth2.RefreshTokenRequest
import com.google.firebase.auth.FirebaseAuthException
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * Controller for handling authentication related endpoints.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val authUtil: AuthUtil,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService // Add RefreshTokenService here
) {

    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    /**
     * Handles Firebase login by verifying the ID token and authenticating the user.
     *
     * @param loginRequest The Firebase login request containing the ID token
     * @return ResponseEntity containing the authentication response
     */
    @PostMapping("/login")
    fun firebaseLogin(@Valid @RequestBody loginRequest: FirebaseLoginRequest): ResponseEntity<AuthResponseBase> {
        return try {
            logger.debug("Processing Firebase login request")
            val response = authService.firebaseLogin(loginRequest)
            
            if (response is SuccessAuthResponse && response.success) {
                logger.info("Successful login for user: ${response.user?.email}")
                ResponseEntity.ok(response)
            } else {
                logger.warn("Login failed: ${(response as FailureAuthResponse).message}")
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response)
            }
        } catch (e: FirebaseAuthException) {
            logger.error("Firebase authentication error: ${e.message}", e)
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed: ${e.message}",
                e
            )
        } catch (e: BadCredentialsException) {
            logger.warn("Invalid credentials: ${e.message}")
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials",
                e
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during login", e)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                e
            )
        }
    }

    /**
     * Retrieves the current authenticated user's information.
     *
     * @return ResponseEntity containing the user's information
     */
    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<UserInfo> {
        return try {
            logger.debug("Fetching current user info")
            val currentUserId = authUtil.getCurrentUserId()
            val user = authService.getUserById(currentUserId)
                ?: throw UsernameNotFoundException("User not found with ID: $currentUserId")
                
            val userInfo = UserInfo(
                id = user.id,
                name = user.name ?: "",
                email = user.email,
                familyId = user.familyId
            )
            
            logger.debug("Successfully fetched user info for user ID: ${user.id}")
            ResponseEntity.ok(userInfo)
            
        } catch (e: UsernameNotFoundException) {
            logger.warn("User not found: ${e.message}")
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
        } catch (e: Exception) {
            logger.error("Error fetching current user", e)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error fetching user information",
                e
            )
        }
        }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Any> {
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    /**
     * Refreshes the JWT token using a refresh token.
     *
     * @param request The refresh token request containing the refresh token
     * @return ResponseEntity containing the new authentication response
     */
    @PostMapping("/refresh")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ResponseEntity<AuthResponseBase> {
        return try {
            // Validate the refresh token and get user ID
            val userId = refreshTokenService.validateRefreshToken(request.refreshToken)
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(FailureAuthResponse(success = false, message = "Invalid or expired refresh token"))
            }

            // Get user from a database
            val user = authService.getUserById(userId)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(FailureAuthResponse(success = false, message = "User not found"))

            // Generate new JWT token
            val newJwtToken = jwtService.generateToken(user.id)

            // Generate new refresh token (rotate refresh tokens for security)
            val newRefreshToken = refreshTokenService.generateRefreshToken(user.id)

            val response = SuccessAuthResponse(
                success = true,
                message = "Token refreshed successfully",
                token = newJwtToken,
                refreshToken = newRefreshToken,
                user = UserInfo(
                    id = user.id,
                    name = user.name ?: "",
                    email = user.email,
                    familyId = user.familyId
                )
            )

            logger.info("Token refreshed for user: ${user.email}")
            ResponseEntity.ok(response)

        } catch (e: Exception) {
            logger.error("Token refresh failed", e)
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(FailureAuthResponse(success = false, message = "Token refresh failed"))
        }
    }

    /**
     * Validates the JWT token for the authenticated user.
     *
     * @param authHeader The authorization header containing the Bearer token
     * @return ResponseEntity containing the validation result
     */
    @PostMapping("/validate")
    fun validateToken(@RequestHeader("Authorization") authHeader: String): ResponseEntity<Map<String, Any>> {
        return try {
            if (!authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("valid" to false, "message" to "Invalid authorization header"))
            }

            val token = authHeader.substring(7)
            val isValid = jwtService.isTokenValid(token)
            val userId = jwtService.extractUserId(token)

            if (isValid && userId != null) {
                ResponseEntity.ok(mapOf(
                    "valid" to true,
                    "userId" to userId,
                    "expiresIn" to (jwtService.getTokenExpirationTime(token) ?: 0)
                ))
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("valid" to false, "message" to "Token expired or invalid"))
            }
        } catch (e: Exception) {
            logger.error("Token validation failed", e)
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("valid" to false, "message" to "Token validation failed"))
        }
    }
}
