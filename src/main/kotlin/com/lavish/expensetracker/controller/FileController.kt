package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.FileStorageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.NoSuchFileException

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = ["*"], maxAge = 3600)
class FileController(
    private val fileStorageService: FileStorageService
) {
    private val logger = LoggerFactory.getLogger(FileController::class.java)

    @GetMapping("/profile-pics/{fileName}")
    fun serveProfilePicture(@PathVariable fileName: String): ResponseEntity<InputStreamResource> {
        logger.debug("Serving profile picture: {}", fileName)

        return try {
            val filePath = fileStorageService.getFilePath(fileName)
            logger.debug("Resolved file path: {}", filePath.toAbsolutePath())

            if (!Files.exists(filePath)) {
                logger.warn("File not found: {}", filePath.toAbsolutePath())
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $fileName")
            }

            if (!Files.isReadable(filePath)) {
                logger.error("File not readable: {}", filePath.toAbsolutePath())
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "File not accessible: $fileName")
            }

            val fileSize = Files.size(filePath)
            logger.debug("File size: {} bytes", fileSize)

            val inputStream = Files.newInputStream(filePath)
            val resource = InputStreamResource(inputStream)

            // Determine content type
            val contentType = Files.probeContentType(filePath) ?: "application/octet-stream"
            logger.debug("Content type: {}", contentType)

            logger.info("Successfully serving file: {} (size: {} bytes)", fileName, fileSize)

            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$fileName\"")
                .header("X-Served-By", "spring-boot")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(fileSize)
                .body(resource)

        } catch (ex: NoSuchFileException) {
            logger.error("File not found: {}", fileName, ex)
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $fileName")
        } catch (ex: ResponseStatusException) {
            // Re-throw ResponseStatusException as-is
            throw ex
        } catch (ex: Exception) {
            logger.error("Error serving file: {}", fileName, ex)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serving file: ${ex.message}")
        }
    }

    @GetMapping("/debug/uploads")
    fun debugUploadsDirectory(): ResponseEntity<Map<String, Any>> {
        logger.info("Debug request for uploads directory")

        return try {
            val uploadInfo = fileStorageService.getUploadDirectoryInfo()
            ResponseEntity.ok(uploadInfo)
        } catch (ex: Exception) {
            logger.error("Error getting upload directory info", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to get directory info: ${ex.message}"))
        }
    }
}
