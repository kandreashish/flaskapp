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

    fun uploadProfilePicture(file: MultipartFile, userId: String): String {
        logger.debug("Starting profile picture upload for user: {}, file: {}, size: {}", userId, file.originalFilename, file.size)

        try {
            // Validate the file first
            validateFile(file)
            logger.debug("File validation passed for user: {}", userId)

            val fileName = generateFileName(file.originalFilename, userId)
            val targetLocation = Paths.get(uploadDir, "profile-pics", fileName)

            logger.debug("Target location: {}", targetLocation)

            // Ensure the directory exists
            ensureDirectoryExists(targetLocation.parent)

            // Check available disk space before upload
            checkDiskSpace(targetLocation.parent, file.size)

            // Perform the file copy operation
            try {
                file.inputStream.use { inputStream ->
                    Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)
                }
                logger.info("Successfully uploaded profile picture for user: {}, file: {}", userId, fileName)
            } catch (ex: IOException) {
                logger.error("IOException during file copy for user: {}, file: {}", userId, fileName, ex)
                when (ex) {
                    is NoSuchFileException -> {
                        throw ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Upload directory not found. Please contact administrator."
                        )
                    }
                    is AccessDeniedException -> {
                        throw ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Permission denied. Unable to write to upload directory."
                        )
                    }
                    is FileSystemException -> {
                        throw ResponseStatusException(
                            HttpStatus.INSUFFICIENT_STORAGE,
                            "File system error. Possibly insufficient disk space."
                        )
                    }
                    else -> {
                        throw ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Failed to store file $fileName: ${ex.message}"
                        )
                    }
                }
            }

            // Verify the file was actually written and has the correct size
            try {
                if (!Files.exists(targetLocation)) {
                    logger.error("File was not created at target location: {}", targetLocation)
                    throw ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "File upload verification failed - file not found after upload"
                    )
                }

                val uploadedFileSize = Files.size(targetLocation)
                if (uploadedFileSize != file.size) {
                    logger.error("File size mismatch. Expected: {}, Actual: {}", file.size, uploadedFileSize)
                    // Clean up the corrupted file
                    Files.deleteIfExists(targetLocation)
                    throw ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "File upload verification failed - size mismatch"
                    )
                }

                logger.debug("File upload verification passed for: {}", fileName)
            } catch (ex: IOException) {
                logger.error("Error during file verification for: {}", fileName, ex)
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to verify uploaded file"
                )
            }

            return "http://103.80.162.46/api/files/profile-pics/$fileName"

        } catch (ex: ResponseStatusException) {
            // Re-throw ResponseStatusException as-is
            logger.warn("Upload failed for user: {} - {}", userId, ex.reason)
            throw ex
        } catch (ex: SecurityException) {
            logger.error("Security exception during upload for user: {}", userId, ex)
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Security policy prevents file upload"
            )
        } catch (ex: OutOfMemoryError) {
            logger.error("Out of memory during upload for user: {}", userId, ex)
            throw ResponseStatusException(
                HttpStatus.INSUFFICIENT_STORAGE,
                "File too large to process"
            )
        } catch (ex: Exception) {
            logger.error("Unexpected error during profile picture upload for user: {}", userId, ex)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred during file upload: ${ex.javaClass.simpleName}"
            )
        }
    }

    /**
     * Ensures the target directory exists and creates it if necessary
     */
    private fun ensureDirectoryExists(directory: Path) {
        try {
            if (!Files.exists(directory)) {
                logger.debug("Creating directory: $directory")
                Files.createDirectories(directory)
            }
        } catch (ex: IOException) {
            logger.error("Failed to create directory: $directory", ex)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to create upload directory"
            )
        } catch (ex: SecurityException) {
            logger.error("Security exception creating directory: $directory", ex)
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
}
