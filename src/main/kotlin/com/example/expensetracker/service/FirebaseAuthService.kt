package com.example.expensetracker.service

import com.example.expensetracker.exception.CustomFirebaseAuthException
import com.example.expensetracker.model.auth.FirebaseUserInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException as FirebaseAuthSdkException
import com.google.firebase.auth.FirebaseToken
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import com.google.firebase.FirebaseApp
import jakarta.annotation.PostConstruct

@Service
class FirebaseAuthService {

    private val logger = LoggerFactory.getLogger(FirebaseAuthService::class.java)
    private lateinit var firebaseAuth: FirebaseAuth

    @Autowired
    private lateinit var firebaseApp: FirebaseApp

    /**
     * Initializes FirebaseAuth instance after the service is constructed.
     * This method is called automatically by Spring after dependency injection.
     */
    @PostConstruct
    fun initialize() {
        try {
            logger.info("Initializing FirebaseAuth using injected FirebaseApp: ${firebaseApp.name}")
            firebaseAuth = FirebaseAuth.getInstance(firebaseApp)
            logger.info("FirebaseAuth initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize FirebaseAuth: ${e.message}", e)
            throw RuntimeException("Firebase Authentication initialization failed", e)
        }
    }

    /**
     * Verifies the ID token and returns the associated user information
     * @param idToken The Firebase ID token to verify
     * @return FirebaseUserInfo if token is valid
     * @throws CustomFirebaseAuthException if the token is invalid or verification fails
     */
    @Throws(CustomFirebaseAuthException::class)
    fun verifyIdToken(idToken: String): FirebaseUserInfo {
        if (!StringUtils.hasText(idToken)) {
            throw Exception("ID token cannot be empty")
        }

        return try {
            logger.debug("Verifying ID token")
            val decodedToken: FirebaseToken = firebaseAuth.verifyIdToken(idToken)
            
            if (!decodedToken.isEmailVerified) {
                logger.warn("Email not verified for user: ${decodedToken.email}")
            }
            
            FirebaseUserInfo(
                uid = decodedToken.uid,
                name = decodedToken.name ?: "",
                email = decodedToken.email ?: "",
                picture = decodedToken.picture ?: ""
            ).also {
                logger.debug("Successfully verified ID token for user: ${it.uid}")
            }
        } catch (e: FirebaseAuthSdkException) {
            val errorMsg = when (e.errorCode.name) {
                "id-token-expired" -> "Firebase ID token has expired"
                "id-token-revoked" -> "Firebase ID token has been revoked"
                "invalid-id-token" -> "Invalid Firebase ID token"
                else -> "Failed to verify ID token: ${e.message}"
            }
            logger.error(errorMsg, e)
            throw CustomFirebaseAuthException( errorMsg,e)
        } catch (e: Exception) {
            val errorMsg = "Unexpected error verifying ID token: ${e.message}"
            logger.error(errorMsg, e)
            throw CustomFirebaseAuthException(errorMsg, e)
        }
    }

    /**
     * Retrieves user information by UID
     * @param uid The Firebase UID of the user to fetch
     * @return FirebaseUserInfo if user exists
     * @throws CustomFirebaseAuthException if a user is not found or operation fails
     */
    @Throws(CustomFirebaseAuthException::class)
    fun getUserById(uid: String): FirebaseUserInfo {
        if (!StringUtils.hasText(uid)) {
            throw Exception("User ID cannot be empty")
        }

        return try {
            logger.debug("Fetching user by ID: $uid")
            val userRecord = firebaseAuth.getUser(uid)
            
            FirebaseUserInfo(
                uid = userRecord.uid,
                name = userRecord.displayName ?: "",
                email = userRecord.email ?: "",
                picture = userRecord.photoUrl?.toString() ?: ""
            ).also {
                logger.debug("Successfully fetched user: ${userRecord.uid}")
            }
        } catch (e: FirebaseAuthSdkException) {
            val errorMsg = when (e.errorCode.name) {
                "user-not-found" -> "No user record found for the provided UID: $uid"
                else -> "Failed to fetch user: ${e.message}"
            }
            logger.error(errorMsg, e)
            throw CustomFirebaseAuthException( errorMsg,e)
        } catch (e: Exception) {
            val errorMsg = "Unexpected error fetching user: ${e.message}"
            logger.error(errorMsg, e)
            throw CustomFirebaseAuthException(errorMsg, e)
        }
    }
}
