package com.lavish.expensetracker.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @PostConstruct
    fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                logger.info("Initializing Firebase App...")

                // Force loading from environment variables for consistency
                logger.info("Loading Firebase credentials from environment variables")
                val credentials = loadCredentialsFromEnvironment()

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
        val privateKeyId = System.getenv("FIREBASE_PRIVATE_KEY_ID")
            ?: throw IllegalArgumentException("FIREBASE_PRIVATE_KEY_ID environment variable is required")
        val privateKey = System.getenv("FIREBASE_PRIVATE_KEY")
            ?: throw IllegalArgumentException("FIREBASE_PRIVATE_KEY environment variable is required")
        val clientEmail = System.getenv("FIREBASE_CLIENT_EMAIL")
            ?: throw IllegalArgumentException("FIREBASE_CLIENT_EMAIL environment variable is required")
        val clientId = System.getenv("FIREBASE_CLIENT_ID")
            ?: throw IllegalArgumentException("FIREBASE_CLIENT_ID environment variable is required")

        logger.info("Loading Firebase credentials from environment variables for project: $projectId")
        logger.debug("Private key preview: ${privateKey.take(50)}...")

        // The private key from environment already contains \n literals, so we need to convert them to actual newlines
        val escapedPrivateKey = privateKey.replace("\\n", "\n")

        val serviceAccountJson = """
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
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/${clientEmail.replace("@", "%40")}",
            "universe_domain": "googleapis.com"
        }
        """.trimIndent()

        logger.debug("Service account JSON structure created successfully")
        logger.debug("Generated JSON preview: ${serviceAccountJson.take(200)}...")

        return GoogleCredentials.fromStream(serviceAccountJson.byteInputStream())
    }
}
