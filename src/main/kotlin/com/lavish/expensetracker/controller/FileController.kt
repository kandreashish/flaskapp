package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.FtpFileStorageService
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

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
                .body(resource)

        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serving file")
        }
    }
}
