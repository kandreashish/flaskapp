package com.lavish.expensetracker.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Service
class FileStorageService {

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
        validateFile(file)

        val fileName = generateFileName(file.originalFilename, userId)
        val targetLocation = Paths.get(uploadDir, "profile-pics", fileName)

        try {
            Files.copy(file.inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)
        } catch (ex: IOException) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to store file $fileName", ex
            )
        }

        return "http://103.80.162.46/api/files/profile-pics/$fileName"
    }

    fun deleteProfilePicture(profilePicUrl: String): Boolean {
        return try {
            val fileName = extractFileNameFromUrl(profilePicUrl)
            if (fileName != null) {
                val filePath = Paths.get(uploadDir, "profile-pics", fileName)
                Files.deleteIfExists(filePath)
            } else {
                false
            }
        } catch (ex: Exception) {
            false
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
}
