package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.FtpFileStorageService
import org.springframework.core.io.InputStreamResource
import org.springframework.http.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

@RestController
@RequestMapping("/api/files")
class FileController(
    private val ftpFileStorageService: FtpFileStorageService
) {

    @GetMapping("/profile-pics/{fileName}")
    fun serveProfilePicture(@PathVariable fileName: String): ResponseEntity<InputStreamResource> {
        return try {
            if (!ftpFileStorageService.fileExists(fileName)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
            }

            val inputStream = ftpFileStorageService.getFileInputStream(fileName)
            val resource = InputStreamResource(inputStream)

            // Determine content type based on file extension
            val contentType = when (fileName.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }

            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$fileName\"")
                .contentType(MediaType.parseMediaType(contentType))
                // Add aggressive caching for images (they rarely change)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30))
                    .cachePublic()
                    .mustRevalidate())
                // Add ETag for better cache validation
                .eTag("\"${fileName}\"")
                // Enable Accept-Ranges for partial content support
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                // Add Content-Length if available (helps with progress indicators)
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .body(resource)

        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serving file")
        }
    }
}
