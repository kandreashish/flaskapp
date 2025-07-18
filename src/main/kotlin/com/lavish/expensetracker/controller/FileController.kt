package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.FileStorageService
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
class FileController(
    private val fileStorageService: FileStorageService
) {

    @GetMapping("/profile-pics/{fileName}")
    fun serveProfilePicture(@PathVariable fileName: String): ResponseEntity<InputStreamResource> {
        return try {
            val filePath = fileStorageService.getFilePath(fileName)

            if (!Files.exists(filePath)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
            }

            val inputStream = Files.newInputStream(filePath)
            val resource = InputStreamResource(inputStream)

            // Determine content type
            val contentType = Files.probeContentType(filePath) ?: "application/octet-stream"

            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$fileName\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource)

        } catch (ex: NoSuchFileException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serving file")
        }
    }
}
