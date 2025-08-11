package com.lavish.expensetracker.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Bean
    fun firebaseApp(): FirebaseApp {
        return try {
            // Check if FirebaseApp is already initialized
            if (FirebaseApp.getApps().isNotEmpty()) {
                logger.info("FirebaseApp already initialized, returning existing instance")
                FirebaseApp.getInstance()
            } else {
                logger.info("Initializing FirebaseApp from environment variables")
                val credentials = loadCredentialsFromEnvironment()
                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()

                val app = FirebaseApp.initializeApp(options)
                logger.info("FirebaseApp initialized successfully with project: ${app.options.projectId}")
                app
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize FirebaseApp: ${e.message}", e)
            throw RuntimeException("Firebase initialization failed", e)
        }
    }

    private fun loadCredentialsFromEnvironment(): GoogleCredentials {
        val projectId = System.getenv("FIREBASE_PROJECT_ID")
            ?: throw IllegalArgumentException("FIREBASE_PROJECT_ID environment variable is required")
        val privateKeyId = System.getenv("FIREBASE_PRIVATE_KEY_ID")
            ?: throw IllegalArgumentException("FIREBASE_PRIVATE_KEY_ID environment variable is required")
        val privateKey = System.getenv("FIREBASE_PRIVATE_KEY")
            ?: throw IllegalArgumentException("FIREBASE_PRIVATE_KEY environment variable is required")
        val clientEmail = System.getenv("FIREBASE_CLIENT_EMAIL")
            ?: throw IllegalArgumentException("FIREBASE_CLIENT_EMAIL environment variable is required")
        val clientId = System.getenv("FIREBASE_CLIENT_ID")
            ?: throw IllegalArgumentException("FIREBASE_CLIENT_ID environment variable is required")

        logger.info("Loading Firebase credentials from environment variables for project: $projectId")

        // Try different approaches to handle the private key
        val serviceAccountJson = try {
            // First, try using the private key as-is (raw format)
            logger.debug("Attempting to use private key as-is")
            createServiceAccountJson(projectId, privateKeyId, privateKey, clientEmail, clientId)
        } catch (e: Exception) {
            logger.debug("Raw private key failed, trying decoded format")
            try {
                // If that fails, try decoding it first
                val decodedKey = String(Base64.getDecoder().decode(privateKey.trim()))
                createServiceAccountJson(projectId, privateKeyId, decodedKey, clientEmail, clientId)
            } catch (e2: Exception) {
                logger.debug("Both raw and decoded failed, trying with PEM formatting")
                // Last resort: try with our custom formatting
                val cleanPrivateKey = cleanAndFormatPrivateKey(privateKey)
                createServiceAccountJson(projectId, privateKeyId, cleanPrivateKey, clientEmail, clientId)
            }
        }

        return try {
            val inputStream = ByteArrayInputStream(serviceAccountJson.toByteArray())
            val credentials = GoogleCredentials.fromStream(inputStream)
            logger.info("Firebase credentials loaded successfully")
            credentials
        } catch (e: IOException) {
            logger.error("Failed to load Firebase credentials: ${e.message}", e)
            logger.debug("Service account JSON preview: ${serviceAccountJson.take(200)}...")

            // If all else fails, try a simpler approach without any private key processing
            logger.debug("Trying simplified approach with minimal key processing")
            try {
                val simplifiedJson = createSimplifiedServiceAccountJson(projectId, privateKeyId, privateKey, clientEmail, clientId)
                val inputStream2 = ByteArrayInputStream(simplifiedJson.toByteArray())
                GoogleCredentials.fromStream(inputStream2)
            } catch (e2: IOException) {
                logger.error("Simplified approach also failed: ${e2.message}", e2)
                throw RuntimeException("Failed to load Firebase credentials from environment variables", e)
            }
        }
    }

    private fun cleanAndFormatPrivateKey(privateKey: String): String {
        try {
            logger.debug("Original private key length: ${privateKey.length}")

            // First, try to detect if this is already a properly formatted PEM key
            if (privateKey.contains("-----BEGIN") && privateKey.contains("-----END")) {
                logger.debug("Private key appears to be already in PEM format")
                // Just normalize line endings
                return privateKey
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .trim()
            }

            // Check if it's base64 encoded by attempting to decode
            val decodedKey = try {
                val decoded = String(Base64.getDecoder().decode(privateKey.trim()))
                logger.debug("Successfully decoded base64 private key")
                decoded
            } catch (e: IllegalArgumentException) {
                logger.debug("Private key is not base64 encoded, using as-is")
                // Not base64 encoded, use as is
                privateKey
            }

            // Clean the private key but preserve base64 characters
            var cleanKey = decodedKey
                .replace("\\n", "\n")       // Replace literal \n with actual newlines
                .replace("\\r", "\r")       // Replace literal \r with actual carriage returns
                .replace("\"", "")          // Remove any quotes
                .trim()

            // Ensure proper PEM format
            if (!cleanKey.startsWith("-----BEGIN PRIVATE KEY-----") &&
                !cleanKey.startsWith("-----BEGIN RSA PRIVATE KEY-----")) {
                logger.debug("Adding PEM headers to private key")
                // Add PEM headers if missing
                cleanKey = "-----BEGIN PRIVATE KEY-----\n$cleanKey\n-----END PRIVATE KEY-----"
            }

            // Ensure proper line breaks in PEM format
            val lines = cleanKey.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val formattedLines = mutableListOf<String>()

            for (line in lines) {
                if (line.startsWith("-----BEGIN") || line.startsWith("-----END")) {
                    formattedLines.add(line)
                } else {
                    // Break long lines into 64-character chunks (standard PEM format)
                    // But preserve base64 characters including +, /, and =
                    line.chunked(64).forEach { chunk ->
                        formattedLines.add(chunk)
                    }
                }
            }

            val finalKey = formattedLines.joinToString("\n")
            logger.debug("Final formatted private key length: ${finalKey.length}")
            return finalKey

        } catch (e: Exception) {
            logger.error("Error cleaning private key: ${e.message}", e)
            throw RuntimeException("Failed to clean private key", e)
        }
    }

    private fun createServiceAccountJson(
        projectId: String,
        privateKeyId: String,
        privateKey: String,
        clientEmail: String,
        clientId: String
    ): String {
        // Escape the private key for JSON
        val escapedPrivateKey = privateKey
            .replace("\\", "\\\\")      // Escape backslashes
            .replace("\"", "\\\"")      // Escape quotes
            .replace("\n", "\\n")       // Escape newlines for JSON
            .replace("\r", "\\r")       // Escape carriage returns for JSON

        return """
        {
            "type": "service_account",
            "project_id": "$projectId",
            "private_key_id": "$privateKeyId",
            "private_key": "$escapedPrivateKey",
            "client_email": "$clientEmail",
            "client_id": "$clientId",
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
            "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/$clientEmail"
        }
        """.trimIndent()
    }

    private fun createSimplifiedServiceAccountJson(
        projectId: String,
        privateKeyId: String,
        privateKey: String,
        clientEmail: String,
        clientId: String
    ): String {
        return """
        {
            "type": "service_account",
            "project_id": "$projectId",
            "private_key_id": "$privateKeyId",
            "private_key": "$privateKey",
            "client_email": "$clientEmail",
            "client_id": "$clientId",
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
            "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/$clientEmail"
        }
        """.trimIndent()
    }
}
