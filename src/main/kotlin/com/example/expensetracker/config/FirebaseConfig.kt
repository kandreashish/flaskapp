package com.example.expensetracker.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.io.IOException
import jakarta.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Value("\${firebase.type:service_account}")
    private lateinit var type: String

    @Value("\${firebase.project-id:#{null}}")
    private var projectId: String? = null

    @Value("\${firebase.private-key-id:#{null}}")
    private var privateKeyId: String? = null

    @Value("\${firebase.private-key:#{null}}")
    private var privateKey: String? = null

    @Value("\${firebase.client-email:#{null}}")
    private var clientEmail: String? = null

    @Value("\${firebase.client-id:#{null}}")
    private var clientId: String? = null

    @Value("\${firebase.auth-uri:https://accounts.google.com/o/oauth2/auth}")
    private lateinit var authUri: String

    @Value("\${firebase.token-uri:https://oauth2.googleapis.com/token}")
    private lateinit var tokenUri: String

    @Value("\${firebase.auth-provider-cert-url:https://www.googleapis.com/oauth2/v1/certs}")
    private lateinit var authProviderCertUrl: String

    @Value("\${firebase.client-cert-url:#{null}}")
    private var clientCertUrl: String? = null

    @PostConstruct
    fun validateConfiguration() {
        val missingProperties = mutableListOf<String>()

        if (projectId.isNullOrBlank()) missingProperties.add("firebase.project-id")
        if (privateKeyId.isNullOrBlank()) missingProperties.add("firebase.private-key-id")
        if (privateKey.isNullOrBlank()) missingProperties.add("firebase.private-key")
        if (clientEmail.isNullOrBlank()) missingProperties.add("firebase.client-email")
        if (clientId.isNullOrBlank()) missingProperties.add("firebase.client-id")
        if (clientCertUrl.isNullOrBlank()) missingProperties.add("firebase.client-cert-url")

        if (missingProperties.isNotEmpty()) {
            logger.error("Missing required Firebase configuration properties: ${missingProperties.joinToString(", ")}")
            logger.error("Please ensure all Firebase environment variables are properly set")
            throw IllegalStateException("Firebase configuration incomplete. Missing: ${missingProperties.joinToString(", ")}")
        }

        logger.info("Firebase configuration validation passed")
    }

    @Bean
    fun firebaseApp(): FirebaseApp {
        try {
            logger.info("Initializing Firebase with project ID: $projectId")

            // Create service account JSON from environment variables
            val serviceAccountJson = """
            {
              "type": "$type",
              "project_id": "$projectId",
              "private_key_id": "$privateKeyId",
              "private_key": "${privateKey!!.replace("\\n", "\n")}",
              "client_email": "$clientEmail",
              "client_id": "$clientId",
              "auth_uri": "$authUri",
              "token_uri": "$tokenUri",
              "auth_provider_x509_cert_url": "$authProviderCertUrl",
              "client_x509_cert_url": "$clientCertUrl"
            }
            """.trimIndent()

            val credentials = GoogleCredentials.fromStream(
                ByteArrayInputStream(serviceAccountJson.toByteArray())
            )

            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId!!)
                .build()

            val app = if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
            } else {
                FirebaseApp.getInstance()
            }

            logger.info("Firebase initialized successfully with project: $projectId")
            return app

        } catch (e: IOException) {
            logger.error("Failed to initialize Firebase: ${e.message}", e)
            throw RuntimeException("Firebase initialization failed: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error during Firebase initialization: ${e.message}", e)
            throw RuntimeException("Firebase initialization failed due to unexpected error", e)
        }
    }
}
