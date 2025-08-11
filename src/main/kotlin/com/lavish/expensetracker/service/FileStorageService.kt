package com.lavish.expensetracker.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Acl
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.firebase.FirebaseApp
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
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

            // Load credentials the same way as FirebaseConfig to ensure consistency
            val resource = ClassPathResource("serviceAccountKey.json")
            val credentials = GoogleCredentials.fromStream(resource.inputStream)

            // Use the same credentials approach as FirebaseConfig
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

    fun uploadProfilePicture(file: MultipartFile, userId: String): String {
        logger.info(
            "=== STARTING PROFILE PICTURE UPLOAD ===\n" +
            "User ID: {}\n" +
            "Original filename: {}\n" +
            "File size: {} bytes ({} KB)\n" +
            "Content type: {}\n" +
            "Firebase bucket: {}",
            userId,
            file.originalFilename,
            file.size,
            file.size / 1024,
            file.contentType,
            firebaseStorageBucket
        )

        val maxAttempts = 3
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                logger.info("=== UPLOAD ATTEMPT {} of {} ===", attempt, maxAttempts)

                // Validate the file first
                logger.debug("Step 1: Validating file...")
                validateFile(file)
                logger.info("✓ File validation passed - Size: {} bytes, Type: {}", file.size, file.contentType)

                // Generate consistent file name
                val fileName = generateConsistentFileName(userId)
                val objectPath = "profile-pics/$fileName"
                logger.info("Step 2: Generated file path: {}", objectPath)

                // Create blob info with metadata
                val blobId = BlobId.of(firebaseStorageBucket, objectPath)
                val metadata = mapOf(
                    "userId" to userId,
                    "uploadedAt" to System.currentTimeMillis().toString(),
                    "originalFilename" to (file.originalFilename ?: "unknown"),
                    "uploadAttempt" to attempt.toString()
                )

                val blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.contentType ?: "image/jpeg")
                    .setMetadata(metadata)
                    .build()

                logger.info("Step 3: Created blob info with metadata: {}", metadata)

                // Upload file to Firebase Storage
                logger.info("Step 4: Uploading to Firebase Storage...")
                val startTime = System.currentTimeMillis()
                val blob = storage.create(blobInfo, file.bytes)
                val uploadTime = System.currentTimeMillis() - startTime

                if (blob == null) {
                    throw RuntimeException("Failed to create blob in Firebase Storage - blob is null")
                }

                logger.info(
                    "✓ File uploaded successfully!\n" +
                    "Upload time: {} ms\n" +
                    "Blob ID: {}\n" +
                    "Blob size: {} bytes\n" +
                    "Blob exists: {}",
                    uploadTime,
                    blob.blobId,
                    blob.size,
                    blob.exists()
                )

                // Generate and return download URL
                logger.info("Step 5: Generating download URL...")
                val downloadUrl = generateDownloadUrl(objectPath)

                logger.info(
                    "=== UPLOAD COMPLETED SUCCESSFULLY ===\n" +
                    "User ID: {}\n" +
                    "Object path: {}\n" +
                    "Download URL: {}\n" +
                    "Total attempts: {}\n" +
                    "Final blob size: {} bytes",
                    userId,
                    objectPath,
                    downloadUrl,
                    attempt,
                    blob.size
                )

                return downloadUrl

            } catch (ex: Exception) {
                logger.error(
                    "❌ UPLOAD FAILED - Attempt {} of {}\n" +
                    "User ID: {}\n" +
                    "Error type: {}\n" +
                    "Error message: {}\n" +
                    "Stack trace: {}",
                    attempt,
                    maxAttempts,
                    userId,
                    ex.javaClass.simpleName,
                    ex.message,
                    ex.stackTrace.take(3).joinToString("\n") { "  at $it" }
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
        // Use timestamp to ensure unique filenames for each upload
        val timestamp = System.currentTimeMillis()
        return "profile_${userId}_${timestamp}.jpg"
    }

    private fun generateDownloadUrl(objectPath: String): String {
        logger.info("=== GENERATING DOWNLOAD URL ===")
        logger.info("Object path: {}", objectPath)
        logger.info("Bucket: {}", firebaseStorageBucket)

        return try {
            val blobId = BlobId.of(firebaseStorageBucket, objectPath)
            logger.debug("Created BlobId: {}", blobId)

            // Verify blob exists
            val blob = storage.get(blobId)
            if (blob == null) {
                logger.error("❌ Blob not found after upload! BlobId: {}", blobId)
                throw RuntimeException("Blob not found after upload")
            }

            logger.info("✓ Blob found - Size: {} bytes, Exists: {}, ContentType: {}",
                blob.size, blob.exists(), blob.contentType)

            // Check current ACLs before setting
            try {
                val currentAcls = blob.getAcl()
                logger.info("Current ACLs before setting public access: {}",
                    currentAcls?.map { "${it.entity} -> ${it.role}" } ?: "None")
            } catch (ex: Exception) {
                logger.debug("Could not retrieve current ACLs (this is normal): {}", ex.message)
            }

            // Make the blob publicly readable
            logger.info("Setting public read access...")
            val startAclTime = System.currentTimeMillis()
            try {
                val acl = blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER))
                val aclTime = System.currentTimeMillis() - startAclTime
                logger.info("✓ ACL set successfully in {} ms: {} -> {}",
                    aclTime, acl.entity, acl.role)
            } catch (ex: Exception) {
                logger.warn("⚠️ Failed to set ACL (image may still be accessible): {}", ex.message)
                // Continue anyway as some Firebase Storage configurations don't require explicit ACLs
            }

            // Generate the public URL
            val publicUrl = "https://firebasestorage.googleapis.com/v0/b/$firebaseStorageBucket/o/${objectPath.replace("/", "%2F")}?alt=media"
            logger.info("✓ Generated public URL: {}", publicUrl)

            // Test URL accessibility (optional but helpful for debugging)
            try {
                val testConnection = java.net.URL(publicUrl).openConnection()
                testConnection.connectTimeout = 5000
                testConnection.readTimeout = 5000
                val responseCode = (testConnection as java.net.HttpURLConnection).responseCode
                logger.info("✓ URL accessibility test - Response code: {}", responseCode)
                if (responseCode != 200) {
                    logger.warn("⚠️ URL returned non-200 response: {}. Image may not be immediately accessible.", responseCode)
                }
            } catch (ex: Exception) {
                logger.debug("URL accessibility test failed (this may be normal): {}", ex.message)
            }

            logger.info("=== DOWNLOAD URL GENERATION COMPLETED ===")
            publicUrl

        } catch (ex: Exception) {
            logger.error("❌ FAILED TO GENERATE DOWNLOAD URL\n" +
                "Object path: {}\n" +
                "Bucket: {}\n" +
                "Error: {}\n" +
                "Exception type: {}",
                objectPath, firebaseStorageBucket, ex.message, ex.javaClass.simpleName, ex)

            // Return a fallback URL pattern that your frontend can handle
            val fallbackUrl = "https://firebasestorage.googleapis.com/v0/b/$firebaseStorageBucket/o/${objectPath.replace("/", "%2F")}?alt=media"
            logger.warn("Returning fallback URL: {}", fallbackUrl)
            fallbackUrl
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
