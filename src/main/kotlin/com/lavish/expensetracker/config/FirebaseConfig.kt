package com.lavish.expensetracker.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @PostConstruct
    fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                logger.info("Initializing Firebase App...")

                // Try to load credentials from the service account file first
                val credentials = try {
                    val serviceAccountResource = ClassPathResource("serviceAccountKey.json")
                    if (serviceAccountResource.exists()) {
                        logger.info("Loading Firebase credentials from serviceAccountKey.json")
                        GoogleCredentials.fromStream(serviceAccountResource.inputStream)
                    } else {
                        // Fallback to environment variables
                        logger.info("Loading Firebase credentials from environment variables")
                        loadCredentialsFromEnvironment()
                    }
                } catch (ex: Exception) {
                    logger.warn("Failed to load from serviceAccountKey.json, trying environment variables", ex)
                    loadCredentialsFromEnvironment()
                }

                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("Firebase App initialized successfully")
            } else {
                logger.info("Firebase App already initialized")
            }
        } catch (ex: Exception) {
            logger.error("Failed to initialize Firebase", ex)
            throw RuntimeException("Failed to initialize Firebase: ${ex.message}", ex)
        }
    }

    @Bean
    fun firebaseApp(): FirebaseApp {
        return FirebaseApp.getInstance()
    }

    private fun loadCredentialsFromEnvironment(): GoogleCredentials {
        val projectId = System.getenv("FIREBASE_PROJECT_ID")
            ?: throw IllegalArgumentException("FIREBASE_PROJECT_ID environment variable is required")
        val privateKey = System.getenv("FIREBASE_PRIVATE_KEY")
            ?: throw IllegalArgumentException("FIREBASE_PRIVATE_KEY environment variable is required")
        val clientEmail = System.getenv("FIREBASE_CLIENT_EMAIL")
            ?: throw IllegalArgumentException("FIREBASE_CLIENT_EMAIL environment variable is required")

        val serviceAccountJson = """
        {
            "type": "service_account",
            "project_id": "$projectId",
            "private_key": "${privateKey.replace("\\n", "\n")}",
            "client_email": "$clientEmail",
            "token_uri": "https://oauth2.googleapis.com/token"
        }
        """.trimIndent()

        return GoogleCredentials.fromStream(serviceAccountJson.byteInputStream())
    }
}
