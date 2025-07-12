package com.lavish.expensetracker.service

import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.model.auth.*
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.google.firebase.auth.FirebaseAuthException
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service responsible for handling authentication and user management.
 */
@Service
@Transactional
class AuthService(
    private val firebaseAuthService: FirebaseAuthService,
    private val jwtService: JwtService,
    private val userRepository: ExpenseUserRepository,
    private val refreshTokenService: RefreshTokenService
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * Authenticates a user using Firebase ID token.
     *
     * @param loginRequest The login request containing the Firebase ID token
     * @return AuthResponse containing the authentication result and user information
     * @throws BadCredentialsException if the token is invalid or authentication fails
     */
    @Throws(BadCredentialsException::class)
    fun firebaseLogin(loginRequest: FirebaseLoginRequest): AuthResponseBase {
        logger.debug("Initiating Firebase login")
        
        try {
            // Verify the Firebase ID token
            val firebaseUser = firebaseAuthService.verifyIdToken(loginRequest.idToken)
            logger.debug("Successfully verified Firebase user: ${firebaseUser.email}")

            // Ensure email is available
            if (firebaseUser.email.isNullOrBlank()) {
                val errorMsg = "Email is required but not provided by Firebase"
                logger.error(errorMsg)
                throw BadCredentialsException(errorMsg)
            }

            // Find or create user in the local database
            val user = findOrCreateUser(firebaseUser)
            logger.debug("User found/created with ID: ${user.id}")

            // Generate JWT token for session management
            val jwtToken = jwtService.generateToken(user.id)
            logger.debug("Generated JWT token for user: ${user.id}")

            return createSuccessAuthResponse(user, firebaseUser, jwtToken)
            
        } catch (e: FirebaseAuthException) {
            val errorMsg = "Firebase authentication failed: ${e.message}"
            logger.error(errorMsg, e)
            throw BadCredentialsException(errorMsg, e)
        } catch (e: Exception) {
            val errorMsg = "Authentication failed: ${e.message}"
            logger.error(errorMsg, e)
            throw BadCredentialsException(errorMsg, e)
        }
    }

    /**
     * Finds an existing user by ID or creates a new one if not found.
     */
    private fun findOrCreateUser(firebaseUser: FirebaseUserInfo): ExpenseUser {
        return userRepository.findByEmail(firebaseUser.email!!)?.let { existingUser ->
            // Update Firebase UID if not set or changed
            if (existingUser.firebaseUid != firebaseUser.uid) {
                logger.info("Updating Firebase UID for user: ${existingUser.id}")
                existingUser.firebaseUid = firebaseUser.uid
                userRepository.save(existingUser)
            }
            existingUser
        } ?: run {
            // Create new user from Firebase data
            logger.info("Creating new user for email: ${firebaseUser.email}")
            val newUser = ExpenseUser(
                id = UUID.randomUUID().toString(),
                name = firebaseUser.name?.ifEmpty { firebaseUser.email.substringBefore("@") },
                email = firebaseUser.email,
                firebaseUid = firebaseUser.uid,
                familyId = null,
                profilePic = firebaseUser.picture,
                aliasName = generateUniqueAliasName()
            )
            userRepository.save(newUser)
            newUser
        }
    }

    fun generateUniqueAliasName(): String {
        logger.debug("Generating unique alias name")
        return try {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            var alias: String
            var attempts = 0
            do {
                alias = (1..6)
                    .map { chars.random() }
                    .joinToString("")
                attempts++
                logger.debug("Generated alias attempt $attempts: $alias")
            } while (userRepository.findAll().any { it.aliasName == alias })
            logger.info("Unique alias generated: $alias after $attempts attempts")
            alias
        } catch (ex: Exception) {
            logger.error("Exception in generateUniqueAliasName", ex)
            throw ex
        }
    }

    /**
     * Creates a successful authentication response.
     */
    private fun createSuccessAuthResponse(
        user: ExpenseUser,
        firebaseUser: FirebaseUserInfo,
        token: String
    ): AuthResponseBase {
        logger.debug("Creating success auth response for user: ${user.id}")
        
        // Generate refresh token
        val refreshToken = refreshTokenService.generateRefreshToken(user.id)
        logger.debug("Generated refresh token for user: ${user.id}")

        return SuccessAuthResponse(
            success = true,
            message = "Authentication successful",
            user = ExpenseUser(
                id = user.id,
                name = user.name?.ifEmpty { firebaseUser.name },
                email = user.email,
                familyId = user.familyId,
                profilePic = firebaseUser.picture,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt,
                aliasName = user.aliasName
            ),
            token = token,
            refreshToken = refreshToken
        )
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param userId The ID of the user to retrieve
     * @return The user if found, null otherwise
     */
    @Transactional(readOnly = true)
    fun getUserById(userId: String): ExpenseUser? {
        logger.debug("Fetching user by ID: $userId")
        return userRepository.findById(userId).orElse(null)?.also {
            logger.debug("Found user with ID: $userId")
        } ?: run {
            logger.warn("User not found with ID: $userId")
            null
        }
    }

    /**
     * Retrieves a user by their Firebase UID.
     *
     * @param firebaseUid The Firebase UID of the user to retrieve
     * @return The user if found, null otherwise
     */
    @Transactional(readOnly = true)
    fun getUserByFirebaseUid(firebaseUid: String): ExpenseUser? {
        logger.debug("Fetching user by Firebase UID: $firebaseUid")
        return userRepository.findByFirebaseUid(firebaseUid)?.also {
            logger.debug("Found user with Firebase UID: $firebaseUid")
        } ?: run {
            logger.warn("User not found with Firebase UID: $firebaseUid")
            null
        }
    }
}
