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

    private val maxFileSize = 2 * 1024 * 1024L // 2MB in bytes
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

        var attempt = 0
        val maxAttempts = 3
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            attempt++
            try {
                logger.debug("Upload attempt {} of {} for user: {}", attempt, maxAttempts, userId)

                // Validate the file first
                validateFile(file)
                logger.debug("File validation passed for user: {}", userId)

                val fileName = generateFileName(file.originalFilename, userId)
                val targetLocation = Paths.get(uploadDir, "profile-pics", fileName)

                logger.debug("Target location: {}", targetLocation)

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
                        throw IOException("Temporary file was not created: ${tempLocation}")
                    }

                    val tempFileSize = Files.size(tempLocation)
                    if (tempFileSize != file.size) {
                        Files.deleteIfExists(tempLocation)
                        throw IOException("File size mismatch. Expected: ${file.size}, Got: ${tempFileSize}")
                    }

                    // Move temporary file to final location atomically
                    Files.move(
                        tempLocation,
                        targetLocation,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )

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

                    when (ex) {
                        is NoSuchFileException -> {
                            lastException = ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Upload directory not found. Please contact administrator."
                            )
                        }

                        is AccessDeniedException -> {
                            lastException = ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Permission denied. Unable to write to upload directory."
                            )
                        }

                        is FileSystemException -> {
                            lastException = ResponseStatusException(
                                HttpStatus.INSUFFICIENT_STORAGE,
                                "File system error. Possibly insufficient disk space."
                            )
                        }

                        else -> {
                            lastException = ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to store file $fileName: ${ex.message}"
                            )
                        }
                    }

                    // If this is not the last attempt, and it's a retryAble error, continue
                    if (attempt < maxAttempts && isRetryAbleError(ex)) {
                        logger.warn("Retrying upload for user: {} after error: {}", userId, ex.message)
                        Thread.sleep(100L * attempt) // Brief backoff
                        continue
                    } else {
                        throw lastException
                    }
                }

            } catch (ex: ResponseStatusException) {
                logger.error("Upload failed for user: {} on attempt {}: {}", userId, attempt, ex.reason)
                throw ex
            } catch (ex: Exception) {
                logger.error("Unexpected error during upload for user: {} on attempt {}", userId, attempt, ex)
                lastException = ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error during file upload: ${ex.message}"
                )

                // If this is not the last attempt, continue
                if (attempt < maxAttempts) {
                    Thread.sleep(100L * attempt) // Brief backoff
                    continue
                } else {
                    throw lastException
                }
            }
        }

        // If we get here, all attempts failed
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
                "File size exceeds maximum limit of 2MB"
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

    private fun generateFileName(originalFilename: String?, userId: String): String {
        val extension = getFileExtension(originalFilename)
        val uuid = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        return "profile_${userId}_${timestamp}_${uuid}.$extension"
    }

    private fun getFileExtension(filename: String?): String {
        return filename?.substringAfterLast('.', "jpg") ?: "jpg"
    }

    private fun extractFileNameFromUrl(url: String): String? {
        return try {
            url.substringAfterLast('/')
        } catch (ex: Exception) {
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
            logger.debug("Target file path for deletion: $filePath")

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
}
