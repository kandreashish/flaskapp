package com.example.expensetracker.controller

import com.example.expensetracker.model.auth.*
import com.example.expensetracker.service.AuthService
import com.example.expensetracker.util.AuthUtil
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
    private val authUtil: AuthUtil
) {

    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    /**
     * Handles Firebase login by verifying the ID token and authenticating the user.
     *
     * @param loginRequest The Firebase login request containing the ID token
     * @return ResponseEntity containing the authentication response
     */
    @PostMapping("/login")
    fun firebaseLogin(@Valid @RequestBody loginRequest: FirebaseLoginRequest): ResponseEntity<AuthResponse> {
        return try {
            logger.debug("Processing Firebase login request")
            val response = authService.firebaseLogin(loginRequest)
            
            if (response.success) {
                logger.info("Successful login for user: ${response.user?.email}")
                ResponseEntity.ok(response)
            } else {
                logger.warn("Login failed: ${response.message}")
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
}
