package com.example.expensetracker.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.jar.JarFile

@RestController
@RequestMapping("/api/build")
class BuildInfoController {

    /**
     * Get build JAR details including file information and manifest data
     */
    @GetMapping("/info")
    fun getBuildInfo(): BuildInfoResponse {
        val buildDir = File("build/libs")
        val jarFiles = if (buildDir.exists()) {
            buildDir.listFiles { file -> file.name.endsWith(".jar") }?.toList() ?: emptyList()
        } else {
            emptyList()
        }

        val jarDetails = jarFiles.map { jarFile ->
            JarDetails(
                fileName = jarFile.name,
                filePath = jarFile.absolutePath,
                fileSize = jarFile.length(),
                fileSizeFormatted = formatFileSize(jarFile.length()),
                lastModified = Instant.ofEpochMilli(jarFile.lastModified())
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                manifestInfo = extractManifestInfo(jarFile)
            )
        }

        return BuildInfoResponse(
            buildDirectory = buildDir.absolutePath,
            jarFiles = jarDetails,
            totalJars = jarDetails.size,
            buildTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get detailed manifest information for a specific JAR file
     */
    @GetMapping("/manifest")
    fun getManifestDetails(): List<ManifestEntry> {
        val buildDir = File("build/libs")
        val jarFiles = if (buildDir.exists()) {
            buildDir.listFiles { file -> file.name.endsWith(".jar") }?.toList() ?: emptyList()
        } else {
            emptyList()
        }

        return jarFiles.map { jarFile ->
            ManifestEntry(
                jarFileName = jarFile.name,
                manifestAttributes = extractDetailedManifestInfo(jarFile)
            )
        }
    }

    private fun extractManifestInfo(jarFile: File): Map<String, String> {
        return try {
            JarFile(jarFile).use { jar ->
                val manifest = jar.manifest
                manifest?.mainAttributes?.let { attributes ->
                    mapOf(
                        "Implementation-Title" to (attributes.getValue("Implementation-Title") ?: "Unknown"),
                        "Implementation-Version" to (attributes.getValue("Implementation-Version") ?: "Unknown"),
                        "Implementation-Vendor" to (attributes.getValue("Implementation-Vendor") ?: "Unknown"),
                        "Main-Class" to (attributes.getValue("Main-Class") ?: "Unknown"),
                        "Built-By" to (attributes.getValue("Built-By") ?: "Unknown"),
                        "Build-Jdk" to (attributes.getValue("Build-Jdk") ?: "Unknown"),
                        "Created-By" to (attributes.getValue("Created-By") ?: "Unknown")
                    )
                } ?: emptyMap()
            }
        } catch (e: Exception) {
            mapOf("error" to "Could not read manifest: ${e.message}")
        }
    }

    private fun extractDetailedManifestInfo(jarFile: File): Map<String, String> {
        return try {
            JarFile(jarFile).use { jar ->
                val manifest = jar.manifest
                manifest?.mainAttributes?.let { attributes ->
                    attributes.entries.associate { entry ->
                        entry.key.toString() to entry.value.toString()
                    }
                } ?: emptyMap()
            }
        } catch (e: Exception) {
            mapOf("error" to "Could not read manifest: ${e.message}")
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}

data class BuildInfoResponse(
    val buildDirectory: String,
    val jarFiles: List<JarDetails>,
    val totalJars: Int,
    val buildTimestamp: Long
)

data class JarDetails(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val lastModified: String,
    val manifestInfo: Map<String, String>
)

data class ManifestEntry(
    val jarFileName: String,
    val manifestAttributes: Map<String, String>
)
