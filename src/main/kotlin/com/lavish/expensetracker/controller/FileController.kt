package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.FileStorageService
import org.springframework.http.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileStorageService: FileStorageService
) {

    @GetMapping("/profile-pics/{userId}/refresh")
    fun refreshProfilePictureUrl(@PathVariable userId: String): ResponseEntity<Map<String, String>> {
        return try {
            val refreshedUrl = fileStorageService.refreshDownloadUrl(userId)
            if (refreshedUrl != null) {
                ResponseEntity.ok(mapOf("url" to refreshedUrl))
            } else {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Profile picture not found")
            }
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to refresh URL: ${ex.message}")
        }
    }
}
