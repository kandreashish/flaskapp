package com.lavish.expensetracker.service

import com.google.cloud.storage.Acl
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.firebase.FirebaseApp
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Service
class FileStorageService(
    private val firebaseApp: FirebaseApp  // Inject FirebaseApp to ensure proper initialization order
) {

    private val logger = LoggerFactory.getLogger(FileStorageService::class.java)

    @Value("\${firebase.storage.bucket:}")
    private lateinit var firebaseStorageBucket: String

    private lateinit var storage: Storage

    private val maxFileSize = 1 * 1024 * 1024L // 1MB in bytes
    private val allowedContentTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp"
    )

    @PostConstruct
    fun initializeFirebaseStorage() {
        logger.info("Initializing Firebase Storage")
        try {
            // Use the injected Firebase app instead of getInstance()
            if (firebaseStorageBucket.isBlank()) {
                // Get bucket name from the Firebase app's project ID
                val projectId = firebaseApp.options.projectId
                firebaseStorageBucket = "$projectId.firebasestorage.app"
                logger.info("Using default Firebase Storage bucket: {}", firebaseStorageBucket)
            }

            // Create GoogleCredentials from environment variables (same as FirebaseConfig)
            val credentials = loadCredentialsFromEnvironment()

            // Use the same credentials as Firebase App
            storage = StorageOptions.newBuilder()
                .setProjectId(firebaseApp.options.projectId)
                .setCredentials(credentials)
                .build()
                .service

            // Verify bucket exists by attempting to list it
            try {
                val bucket = storage.get(firebaseStorageBucket)
                if (bucket != null) {
                    logger.info("Firebase Storage bucket {} verified successfully", firebaseStorageBucket)
                } else {
                    logger.info("Firebase Storage bucket {} will be created on first upload", firebaseStorageBucket)
                }
            } catch (ex: Exception) {
                logger.warn("Could not verify bucket existence, but this is normal for new buckets: {}", ex.message)
            }

            logger.info("Firebase Storage initialization completed successfully with bucket: {}", firebaseStorageBucket)

        } catch (ex: Exception) {
            logger.error("Failed to initialize Firebase Storage", ex)
            throw RuntimeException("Failed to initialize Firebase Storage: ${ex.message}", ex)
        }
    }

    private fun loadCredentialsFromEnvironment(): com.google.auth.oauth2.GoogleCredentials {
        val projectId = System.getenv("FIREBASE_PROJECT_ID")
            ?: throw IllegalArgumentException("FIREBASE_PROJECT_ID environment variable is required")
        val privateKeyId = System.getenv("FIREBASE_PRIVATE_KEY_ID")
            ?: throw IllegalArgumentException("FIREBASE_PRIVATE_KEY_ID environment variable is required")
        val privateKey = System.getenv("FIREBASE_PRIVATE_KEY")
            ?: throw IllegalArgumentException("FIREBASE_PRIVATE_KEY environment variable is required")
        val clientEmail = System.getenv("FIREBASE_CLIENT_EMAIL")
            ?: throw IllegalArgumentException("FIREBASE_CLIENT_EMAIL environment variable is required")
        val clientId = System.getenv("FIREBASE_CLIENT_ID")
            ?: throw IllegalArgumentException("FIREBASE_CLIENT_ID environment variable is required")

        logger.info("Loading Firebase credentials from environment variables for project: $projectId")

        // The private key from environment already contains \n literals, so we need to convert them to actual newlines
        val escapedPrivateKey = privateKey.replace("\\n", "\n")

        val serviceAccountJson = """
        {
            "type": "service_account",
            "project_id": "$projectId",
            "private_key_id": "$privateKeyId",
            "private_key": "$escapedPrivateKey",
            "client_email": "$clientEmail",
            "client_id": "$clientId",
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
            "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/${clientEmail.replace("@", "%40")}",
            "universe_domain": "googleapis.com"
        }
        """.trimIndent()

        return com.google.auth.oauth2.GoogleCredentials.fromStream(serviceAccountJson.byteInputStream())
    }

    fun uploadProfilePicture(file: MultipartFile, userId: String): String {
        logger.debug(
            "Starting profile picture upload to Firebase Storage for user: {}, file: {}, size: {}",
            userId,
            file.originalFilename,
            file.size
        )

        val maxAttempts = 3
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                logger.debug("Upload attempt {} of {} for user: {}", attempt, maxAttempts, userId)

                // Validate the file first
                validateFile(file)
                logger.debug("File validation passed for user: {}", userId)

                // Generate consistent file name
                val fileName = generateConsistentFileName(userId)
                val objectPath = "profile-pics/$fileName"

                // Create blob info with metadata
                val blobId = BlobId.of(firebaseStorageBucket, objectPath)
                val blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.contentType ?: "image/jpeg")
                    .setMetadata(mapOf(
                        "userId" to userId,
                        "uploadedAt" to System.currentTimeMillis().toString(),
                        "originalFilename" to (file.originalFilename ?: "unknown")
                    ))
                    .build()

                // Upload file to Firebase Storage
                val blob = storage.create(blobInfo, file.bytes)

                if (blob == null) {
                    throw RuntimeException("Failed to create blob in Firebase Storage")
                }

                logger.info(
                    "Successfully uploaded profile picture for user: {} on attempt {}, path: {}",
                    userId,
                    attempt,
                    objectPath
                )

                // Generate and return download URL
                val downloadUrl = generateDownloadUrl(objectPath)
                return downloadUrl

            } catch (ex: Exception) {
                logger.error(
                    "Error during Firebase Storage upload for user: {}, attempt: {}",
                    userId,
                    attempt,
                    ex
                )

                lastException = when (ex) {
                    is IllegalArgumentException -> {
                        ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid file or parameters: ${ex.message}"
                        )
                    }
                    is RuntimeException -> {
                        if (ex.message?.contains("storage") == true) {
                            ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Firebase Storage error: ${ex.message}"
                            )
                        } else {
                            ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Upload failed: ${ex.message}"
                            )
                        }
                    }
                    else -> {
                        ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Unexpected error during file upload: ${ex.message}"
                        )
                    }
                }

                // Retry for certain types of errors
                if (attempt < maxAttempts && isRetryableError(ex)) {
                    logger.warn("Retrying upload for user: {} after error: {}", userId, ex.message)
                    Thread.sleep(100L * attempt) // Brief backoff
                    continue
                } else {
                    throw lastException
                }
            }
        }

        throw lastException ?: ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "File upload failed after $maxAttempts attempts"
        )
    }

    private fun isRetryableError(ex: Exception): Boolean {
        return when {
            ex.message?.contains("timeout", ignoreCase = true) == true -> true
            ex.message?.contains("network", ignoreCase = true) == true -> true
            ex.message?.contains("connection", ignoreCase = true) == true -> true
            ex is RuntimeException && ex.message?.contains("storage") == true -> true
            else -> false
        }
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Please select a file to upload")
        }

        if (file.size > maxFileSize) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File size exceeds maximum limit of 5MB"
            )
        }

        val contentType = file.contentType
        if (contentType == null || !allowedContentTypes.contains(contentType.lowercase())) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only image files (JPEG, PNG, GIF, WebP) are allowed"
            )
        }
    }

    private fun generateConsistentFileName(userId: String): String {
        // Always use jpg extension for consistency, regardless of original format
        return "profile_${userId}.jpg"
    }

    private fun generateDownloadUrl(objectPath: String): String {
        return try {
            val blobId = BlobId.of(firebaseStorageBucket, objectPath)
            val blob = storage.get(blobId) ?: throw RuntimeException("Blob not found after upload")

            // Make the blob publicly readable
            blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER))

            // Return short public URL instead of signed URL
            val publicUrl = "https://firebasestorage.googleapis.com/v0/b/$firebaseStorageBucket/o/${objectPath.replace("/", "%2F")}?alt=media"
            logger.info("Generated short public URL for path: {}", objectPath)
            publicUrl

        } catch (ex: Exception) {
            logger.error("Failed to generate download URL for path: {}", objectPath, ex)
            // Return a fallback URL pattern that your frontend can handle
            "https://firebasestorage.googleapis.com/v0/b/$firebaseStorageBucket/o/${objectPath.replace("/", "%2F")}?alt=media"
        }
    }

    fun deleteProfilePicture(profilePicUrl: String): Boolean {
        logger.debug("Attempting to delete profile picture from Firebase Storage: {}", profilePicUrl)

        return try {
            val objectPath = extractObjectPathFromUrl(profilePicUrl)
            if (objectPath == null) {
                logger.warn("Could not extract object path from URL: {}", profilePicUrl)
                return false
            }

            val blobId = BlobId.of(firebaseStorageBucket, objectPath)
            val deleted = storage.delete(blobId)

            if (deleted) {
                logger.info("Successfully deleted profile picture from Firebase Storage: {}", objectPath)
            } else {
                logger.warn("Profile picture was not found in Firebase Storage: {}", objectPath)
            }

            return deleted

        } catch (ex: Exception) {
            logger.error("Error while deleting profile picture from Firebase Storage: {}", profilePicUrl, ex)
            false
        }
    }

    private fun extractObjectPathFromUrl(url: String): String? {
        return try {
            when {
                // Handle signed URLs
                url.contains("googleapis.com/v0/b/") -> {
                    val parts = url.split("/o/")
                    if (parts.size >= 2) {
                        val objectPart = parts[1].split("?")[0]
                        java.net.URLDecoder.decode(objectPart, "UTF-8")
                    } else null
                }
                // Handle direct URLs or other formats
                url.contains("profile-pics/") -> {
                    val startIndex = url.indexOf("profile-pics/")
                    url.substring(startIndex)
                }
                else -> null
            }
        } catch (ex: Exception) {
            logger.warn("Failed to extract object path from URL: {}", url, ex)
            null
        }
    }

    /**
     * Get a new download URL for an existing file (useful for URL refresh)
     */
    fun refreshDownloadUrl(userId: String): String? {
        return try {
            val fileName = generateConsistentFileName(userId)
            val objectPath = "profile-pics/$fileName"
            val blobId = BlobId.of(firebaseStorageBucket, objectPath)

            // Check if file exists
            val blob = storage.get(blobId)
            if (blob != null && blob.exists()) {
                generateDownloadUrl(objectPath)
            } else {
                logger.warn("Profile picture not found for user: {}", userId)
                null
            }
        } catch (ex: Exception) {
            logger.error("Failed to refresh download URL for user: {}", userId, ex)
            null
        }
    }
}
