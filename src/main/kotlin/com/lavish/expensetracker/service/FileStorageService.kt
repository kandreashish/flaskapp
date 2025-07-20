package com.lavish.expensetracker.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.nio.file.*
import java.util.*
import javax.annotation.PostConstruct

@Service
class FileStorageService {

    private val logger = LoggerFactory.getLogger(FileStorageService::class.java)

    @Value("\${app.upload.dir:uploads}")
    private lateinit var uploadDir: String

    private val maxFileSize = 10 * 1024 * 1024L // 10MB in bytes
    private val allowedContentTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp"
    )

    @PostConstruct
    fun initializeUploadDirectories() {
        logger.info("Initializing upload directories with uploadDir: {}", uploadDir)
        try {
            val uploadPath = Paths.get(uploadDir)
            val profilePicsPath = Paths.get(uploadDir, "profile-pics")

            logger.info("Upload path: {}", uploadPath.toAbsolutePath())
            logger.info("Profile pics path: {}", profilePicsPath.toAbsolutePath())

            // Create base upload directory
            if (!Files.exists(uploadPath)) {
                logger.info("Creating base upload directory: {}", uploadPath.toAbsolutePath())
                Files.createDirectories(uploadPath)
                logger.info("Successfully created base upload directory")
            } else {
                logger.info("Base upload directory already exists")
            }

            // Create profile-pics subdirectory
            if (!Files.exists(profilePicsPath)) {
                logger.info("Creating profile-pics directory: {}", profilePicsPath.toAbsolutePath())
                Files.createDirectories(profilePicsPath)
                logger.info("Successfully created profile-pics directory")
            } else {
                logger.info("Profile-pics directory already exists")
            }

            // Check permissions
            if (!Files.isWritable(profilePicsPath)) {
                logger.error("Profile-pics directory is not writable: {}", profilePicsPath.toAbsolutePath())
                throw RuntimeException("Upload directory is not writable")
            }

            logger.info("Upload directories initialization completed successfully")

        } catch (ex: Exception) {
            logger.error("Failed to initialize upload directories", ex)
            throw RuntimeException("Failed to initialize upload directories: ${ex.message}", ex)
        }
    }

    fun uploadProfilePicture(file: MultipartFile, userId: String): String {
        logger.debug(
            "Starting profile picture upload for user: {}, file: {}, size: {}",
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

                // Always use jpg extension for consistency, regardless of original format
                val fileName = generateConsistentFileName(userId)
                val targetLocation = Paths.get(uploadDir, "profile-pics", fileName)

                logger.debug("Target location: {}", targetLocation)

                // Delete any existing profile pictures for this user before uploading new one
                //deleteExistingProfilePictures(userId)

                // Ensure the directory exists and is writable
                ensureDirectoryExists(targetLocation.parent)
                verifyDirectoryWritable(targetLocation.parent)

                // Check available disk space before upload
                checkDiskSpace(targetLocation.parent, file.size)

                // Create temporary file first, then move to final location
                val tempLocation = Paths.get(uploadDir, "profile-pics", "${fileName}.tmp")

                try {
                    // Write to temporary file first
                    file.inputStream.use { inputStream ->
                        Files.copy(inputStream, tempLocation, StandardCopyOption.REPLACE_EXISTING)
                    }

                    // Verify temporary file was written correctly
                    if (!Files.exists(tempLocation)) {
                        throw IOException("Temporary file was not created: $tempLocation")
                    }

                    val tempFileSize = Files.size(tempLocation)
                    if (tempFileSize != file.size) {
                        Files.deleteIfExists(tempLocation)
                        throw IOException("File size mismatch. Expected: ${file.size}, Got: ${tempFileSize}")
                    }

                    // Move temporary file to final location with fallback strategy
                    try {
                        // Try atomic move first
                        Files.move(
                            tempLocation,
                            targetLocation,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                        )
                    } catch (atomicMoveEx: Exception) {
                        logger.warn("Atomic move failed for user: {}, falling back to regular move: {}", userId, atomicMoveEx.message)

                        // Fallback to regular move if atomic move fails
                        try {
                            Files.move(
                                tempLocation,
                                targetLocation,
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } catch (regularMoveEx: Exception) {
                            logger.warn("Regular move also failed for user: {}, trying copy and delete: {}", userId, regularMoveEx.message)

                            // Final fallback: copy and then delete
                            Files.copy(tempLocation, targetLocation, StandardCopyOption.REPLACE_EXISTING)
                            Files.deleteIfExists(tempLocation)
                        }
                    }

                    // Verify final file exists and has correct size
                    if (!Files.exists(targetLocation)) {
                        throw IOException("Final file was not created: $targetLocation")
                    }

                    val finalFileSize = Files.size(targetLocation)
                    if (finalFileSize != file.size) {
                        Files.deleteIfExists(targetLocation)
                        throw IOException("Final file size mismatch. Expected: ${file.size}, Got: ${finalFileSize}")
                    }

                    logger.info(
                        "Successfully uploaded profile picture for user: {} on attempt {}, file: {}",
                        userId,
                        attempt,
                        fileName
                    )

                    // Return the URL for accessing the file
                    return "/api/files/profile-pics/$fileName"

                } catch (ex: IOException) {
                    // Clean up any temporary files
                    try {
                        Files.deleteIfExists(tempLocation)
                        Files.deleteIfExists(targetLocation)
                    } catch (cleanupEx: Exception) {
                        logger.warn("Failed to cleanup files after upload error: {}", cleanupEx.message)
                    }

                    logger.error(
                        "IOException during file copy for user: {}, attempt: {}, file: {}",
                        userId,
                        attempt,
                        fileName,
                        ex
                    )

                    // Convert IOException to appropriate ResponseStatusException
                    lastException = when (ex) {
                        is NoSuchFileException -> {
                            ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Upload directory not found. Please contact administrator."
                            )
                        }
                        is AccessDeniedException -> {
                            ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Permission denied. Unable to write to upload directory."
                            )
                        }
                        is FileSystemException -> {
                            ResponseStatusException(
                                HttpStatus.INSUFFICIENT_STORAGE,
                                "File system error. Possibly insufficient disk space."
                            )
                        }
                        else -> {
                            ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to store file $fileName: ${ex.message}"
                            )
                        }
                    }

                    // Check if this error is retry able and if we have attempts left
                    if (attempt < maxAttempts && isRetryAbleError(ex)) {
                        logger.warn("Retrying upload for user: {} after error: {}", userId, ex.message)
                        Thread.sleep(100L * attempt) // Brief backoff
                        continue // Retry the loop
                    } else {
                        // Either last attempt or non-retryable error
                        throw lastException
                    }
                }

            } catch (ex: ResponseStatusException) {
                logger.error("Upload failed for user: {} on attempt {}: {}", userId, attempt, ex.reason)
                // ResponseStatusException should not be retried
                throw ex
            } catch (ex: Exception) {
                logger.error("Unexpected error during upload for user: {} on attempt {}", userId, attempt, ex)
                lastException = ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error during file upload: ${ex.message}"
                )

                // Retry unexpected errors if we have attempts left
                if (attempt < maxAttempts) {
                    Thread.sleep(100L * attempt) // Brief backoff
                    continue // Retry the loop
                } else {
                    throw lastException
                }
            }
        }

        // This should never be reached due to the for loop structure, but kept for safety
        throw lastException ?: ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "File upload failed after $maxAttempts attempts"
        )
    }

    private fun isRetryAbleError(ex: IOException): Boolean {
        return when (ex) {
            is FileSystemException -> true // Might be temporary
            is NoSuchFileException -> false // Directory issue, unlikely to resolve on retry
            is AccessDeniedException -> false // Permission issue, won't resolve on retry
            else -> true // Other IO errors might be temporary
        }
    }

    /**
     * Ensures the target directory exists and creates it if necessary
     */
    private fun ensureDirectoryExists(directory: Path) {
        try {
            if (!Files.exists(directory)) {
                logger.debug("Creating directory: {}", directory)
                Files.createDirectories(directory)
                logger.info("Successfully created directory: {}", directory)
            } else {
                logger.debug("Directory already exists: {}", directory)
            }
        } catch (ex: IOException) {
            logger.error("Failed to create directory: {}", directory, ex)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to create upload directory: ${ex.message}"
            )
        } catch (ex: SecurityException) {
            logger.error("Security exception creating directory: {}", directory, ex)
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Permission denied creating upload directory"
            )
        }
    }

    /**
     * Checks if there's sufficient disk space for the upload
     */
    private fun checkDiskSpace(directory: Path, fileSize: Long) {
        try {
            val store = Files.getFileStore(directory)
            val usableSpace = store.usableSpace
            val requiredSpace = fileSize + (1024 * 1024) // File size + 1MB buffer

            logger.debug("Disk space check - Available: $usableSpace bytes, Required: $requiredSpace bytes")

            if (usableSpace < requiredSpace) {
                logger.error("Insufficient disk space. Available: $usableSpace, Required: $requiredSpace")
                throw ResponseStatusException(
                    HttpStatus.INSUFFICIENT_STORAGE,
                    "Insufficient disk space for file upload"
                )
            }
        } catch (ex: IOException) {
            logger.warn("Could not check disk space for directory: $directory", ex)
            // Continue with upload - disk space check is not critical
        } catch (ex: ResponseStatusException) {
            // Re-throw insufficient storage exception
            throw ex
        }
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Please select a file to upload")
        }

        if (file.size > maxFileSize) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File size exceeds maximum limit of 10MB"
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

    private fun extractFileNameFromUrl(url: String): String? {
        return try {
            url.substringAfterLast('/')
        } catch (_: Exception) {
            null
        }
    }

    fun getFilePath(fileName: String): Path {
        return Paths.get(uploadDir, "profile-pics", fileName)
    }

    fun deleteProfilePicture(profilePicUrl: String): Boolean {
        logger.debug("Attempting to delete profile picture: $profilePicUrl")

        return try {
            val fileName = extractFileNameFromUrl(profilePicUrl)
            if (fileName == null) {
                logger.warn("Could not extract filename from URL: $profilePicUrl")
                return false
            }

            val filePath = Paths.get(uploadDir, "profile-pics", fileName)
            logger.debug("Target file path for deletion: {}", filePath)

            if (!Files.exists(filePath)) {
                logger.warn("Profile picture file does not exist: $filePath")
                return true // Consider it "deleted" if it doesn't exist
            }

            // Check if we have permission to delete the file
            if (!Files.isWritable(filePath.parent)) {
                logger.error("No write permission to delete file: $filePath")
                return false
            }

            val deleted = Files.deleteIfExists(filePath)
            if (deleted) {
                logger.info("Successfully deleted profile picture: $fileName")
            } else {
                logger.warn("File was not deleted (may not have existed): $fileName")
            }

            return deleted

        } catch (ex: SecurityException) {
            logger.error("Security exception while deleting profile picture: $profilePicUrl", ex)
            false
        } catch (ex: AccessDeniedException) {
            logger.error("Access denied while deleting profile picture: $profilePicUrl", ex)
            false
        } catch (ex: DirectoryNotEmptyException) {
            logger.error("Directory not empty while deleting profile picture: $profilePicUrl", ex)
            false
        } catch (ex: IOException) {
            logger.error("IO exception while deleting profile picture: $profilePicUrl", ex)
            false
        } catch (ex: Exception) {
            logger.error("Unexpected error while deleting profile picture: $profilePicUrl", ex)
            false
        }
    }

    private fun verifyDirectoryWritable(directory: Path) {
        if (!Files.isWritable(directory)) {
            logger.error("Directory is not writable: {}", directory.toAbsolutePath())
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Upload directory is not writable"
            )
        }

        // Test write by creating a temporary file
        try {
            val testFile = directory.resolve(".write-test-${UUID.randomUUID()}")
            Files.write(testFile, "test".toByteArray())
            Files.deleteIfExists(testFile)
        } catch (ex: Exception) {
            logger.error("Failed to write test file to directory: {}", directory.toAbsolutePath(), ex)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Upload directory write test failed: ${ex.message}"
            )
        }
    }

    /**
     * Deletes any existing profile pictures for the user
     */
    private fun deleteExistingProfilePictures(userId: String) {
        val fileName = generateConsistentFileName(userId)
        val filePath = Paths.get(uploadDir, "profile-pics", fileName)

        try {
            if (Files.exists(filePath)) {
                logger.info("Deleting existing profile picture for user: $userId, file: $fileName")
                Files.delete(filePath)
                logger.info("Successfully deleted existing profile picture")
            } else {
                logger.info("No existing profile picture found for user: $userId")
            }
        } catch (ex: Exception) {
            logger.error("Failed to delete existing profile picture for user: $userId", ex)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to delete existing profile picture: ${ex.message}"
            )
        }
    }
}
