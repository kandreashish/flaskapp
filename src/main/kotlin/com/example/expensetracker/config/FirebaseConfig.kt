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

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Value("\${firebase.type:service_account}")
    private lateinit var type: String

    @Value("\${firebase.project-id}")
    private lateinit var projectId: String

    @Value("\${firebase.private-key-id}")
    private lateinit var privateKeyId: String

    @Value("\${firebase.private-key}")
    private lateinit var privateKey: String

    @Value("\${firebase.client-email}")
    private lateinit var clientEmail: String

    @Value("\${firebase.client-id}")
    private lateinit var clientId: String

    @Value("\${firebase.auth-uri:https://accounts.google.com/o/oauth2/auth}")
    private lateinit var authUri: String

    @Value("\${firebase.token-uri:https://oauth2.googleapis.com/token}")
    private lateinit var tokenUri: String

    @Value("\${firebase.auth-provider-cert-url:https://www.googleapis.com/oauth2/v1/certs}")
    private lateinit var authProviderCertUrl: String

    @Value("\${firebase.client-cert-url}")
    private lateinit var clientCertUrl: String

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
              "private_key": "${privateKey.replace("\\n", "\n")}",
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
                .setProjectId(projectId)
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
