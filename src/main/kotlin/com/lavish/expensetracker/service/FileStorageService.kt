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
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.annotation.PostConstruct
import javax.imageio.ImageIO

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

    data class UploadResult(val highResUrl: String, val lowResUrl: String)

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

    fun uploadProfilePicture(file: MultipartFile, userId: String): UploadResult {
        logger.info(
            "=== STARTING PROFILE PICTURE UPLOAD (DUAL RES) ===\n" +
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

                // Read original image
                val originalImage = ImageIO.read(file.inputStream)
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported or corrupted image file")

                // Generate file base name (no extension)
                val fileBase = generateConsistentFileNameBase(userId)
                val highObjectPath = "profile-pics/${fileBase}_full.jpg"
                val lowObjectPath = "profile-pics/${fileBase}_low.jpg"
                logger.info("Step 2: Generated file paths: high={}, low={}", highObjectPath, lowObjectPath)

                // Prepare images
                val highResImage = if (needsDownscale(originalImage)) resizeImageMaintainingAspect(originalImage, 1024) else originalImage
                val lowResImage = resizeImageMaintainingAspect(originalImage, 256)

                // Encode images to JPEG byte arrays
                val highBytes = bufferedImageToJpegBytes(highResImage)
                val lowBytes = bufferedImageToJpegBytes(lowResImage, quality = 0.75f)

                // Upload both variants
                val highUrl = uploadBytesAndGetUrl(userId, highObjectPath, highBytes, attempt, variant = "full")
                val lowUrl = uploadBytesAndGetUrl(userId, lowObjectPath, lowBytes, attempt, variant = "low")

                logger.info(
                    "=== DUAL RES UPLOAD COMPLETED SUCCESSFULLY ===\n" +
                    "User ID: {}\n" +
                    "High URL: {}\nLow URL: {}\n" +
                    "Attempts: {}",
                    userId,
                    highUrl,
                    lowUrl,
                    attempt
                )
                return UploadResult(highUrl, lowUrl)

            } catch (ex: Exception) {
                logger.error(
                    "❌ DUAL RES UPLOAD FAILED - Attempt {} of {}\n" +
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
                    is IllegalArgumentException -> ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file or parameters: ${ex.message}")
                    is ResponseStatusException -> ex
                    else -> ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error during file upload: ${ex.message}")
                }
                if (attempt < maxAttempts && isRetryableError(ex)) {
                    Thread.sleep(100L * attempt)
                    continue
                } else {
                    throw lastException
                }
            }
        }
        throw lastException ?: ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File upload failed after $maxAttempts attempts")
    }

    private fun uploadBytesAndGetUrl(userId: String, objectPath: String, bytes: ByteArray, attempt: Int, variant: String): String {
        // Create blob info with metadata
        val blobId = BlobId.of(firebaseStorageBucket, objectPath)
        val metadata = mapOf(
            "userId" to userId,
            "uploadedAt" to System.currentTimeMillis().toString(),
            "variant" to variant,
            "uploadAttempt" to attempt.toString()
        )
        val blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType("image/jpeg")
            .setMetadata(metadata)
            .build()
        val startTime = System.currentTimeMillis()
        val blob = storage.create(blobInfo, bytes)
        val uploadTime = System.currentTimeMillis() - startTime
        logger.info("✓ Uploaded variant '{}' path='{}' size={} bytes in {} ms", variant, objectPath, blob.size, uploadTime)
        return generateDownloadUrl(objectPath)
    }

    private fun needsDownscale(img: BufferedImage): Boolean = img.width > 1024 || img.height > 1024

    private fun resizeImageMaintainingAspect(original: BufferedImage, maxDimension: Int): BufferedImage {
        val (newW, newH) = calculateNewDimensions(original.width, original.height, maxDimension)
        if (newW == original.width && newH == original.height) return original
        val scaledImage = BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = scaledImage.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(original.getScaledInstance(newW, newH, Image.SCALE_SMOOTH), 0, 0, null)
        g.dispose()
        return scaledImage
    }

    private fun calculateNewDimensions(width: Int, height: Int, maxDim: Int): Pair<Int, Int> {
        if (width <= maxDim && height <= maxDim) return width to height
        val ratio = width.toDouble() / height.toDouble()
        return if (ratio > 1) { // landscape
            maxDim to (maxDim / ratio).toInt()
        } else { // portrait
            (maxDim * ratio).toInt() to maxDim
        }
    }

    private fun bufferedImageToJpegBytes(image: BufferedImage, quality: Float = 0.85f): ByteArray {
        // Java ImageIO for JPEG doesn't expose quality easily without ImageWriter; implement custom
        val baos = java.io.ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        val writer = writers.next()
        val ios = ImageIO.createImageOutputStream(baos)
        writer.output = ios
        val param = writer.defaultWriteParam
        if (param.canWriteCompressed()) {
            param.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            param.compressionQuality = quality.coerceIn(0.05f, 1.0f)
        }
        writer.write(null, javax.imageio.IIOImage(image, null, null), param)
        ios.close()
        writer.dispose()
        return baos.toByteArray()
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

    private fun generateConsistentFileNameBase(userId: String): String {
        val timestamp = System.currentTimeMillis()
        return "profile_${userId}_${timestamp}"
    }

    private fun generateDownloadUrl(objectPath: String): String {
        logger.info("=== GENERATING DOWNLOAD URL ===")
        logger.info("Object path: {}", objectPath)
        logger.info("Bucket: {}", firebaseStorageBucket)
        return try {
            val blobId = BlobId.of(firebaseStorageBucket, objectPath)
            val blob = storage.get(blobId) ?: throw RuntimeException("Blob not found after upload")
            try {
                blob.getAcl()
            } catch (_: Exception) { }
            try {
                blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER))
            } catch (_: Exception) { }
            "https://firebasestorage.googleapis.com/v0/b/$firebaseStorageBucket/o/${objectPath.replace("/", "%2F")}?alt=media"
        } catch (ex: Exception) {
            val fallbackUrl = "https://firebasestorage.googleapis.com/v0/b/$firebaseStorageBucket/o/${objectPath.replace("/", "%2F")}?alt=media"
            fallbackUrl
        }
    }

    fun deleteProfilePicture(profilePicUrl: String): Boolean {
        logger.debug("Attempting to delete profile picture from Firebase Storage: {}", profilePicUrl)

        return try {
            val objectPath = extractObjectPathFromUrl(profilePicUrl) ?: return false
            val blobId = BlobId.of(firebaseStorageBucket, objectPath)
            val deleted = storage.delete(blobId)
            deleted
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
            val prefix = "profile-pics/profile_${userId}_"
            logger.info("Refreshing profile picture URL. Listing blobs with prefix: {}", prefix)
            val blobs = storage.list(
                firebaseStorageBucket,
                com.google.cloud.storage.Storage.BlobListOption.prefix(prefix)
            )
            val latestHigh = blobs.values
                .filter { it.name.endsWith("_full.jpg") }
                .maxByOrNull { it.createTime ?: 0L }
            if (latestHigh == null) {
                logger.warn("No high-res profile picture found for user {}", userId)
                null
            } else {
                logger.info("Found latest high-res profile pic blob: {} (size={} bytes)", latestHigh.name, latestHigh.size)
                generateDownloadUrl(latestHigh.name)
            }
        } catch (ex: Exception) {
            logger.error("Failed to refresh download URL for user {}: {}", userId, ex.message)
            null
        }
    }
}
