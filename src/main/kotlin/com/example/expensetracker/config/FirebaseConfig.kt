package com.example.expensetracker.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.IOException


@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Bean
    fun firebaseApp(): FirebaseApp {
        try {
            // Better way to load from classpath
            val resource = ClassPathResource("serviceAccountKey.json")
            val credentials = GoogleCredentials.fromStream(resource.inputStream)

            val options = FirebaseOptions.builder()
                .setCredentials(credentials) // Reuse the already created credentials
                .build()

            return if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options).also {
                    logger.info("Firebase initialized successfully with project: ${credentials.quotaProjectId}")
                }
            } else {
                FirebaseApp.getInstance().also {
                    logger.info("Using existing Firebase app")
                }
            }

        } catch (e: IOException) {
            logger.error("Failed to initialize Firebase: ${e.message}", e)
            throw RuntimeException("Firebase initialization failed: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Unexpected error during Firebase initialization: ${e.message}", e)
            throw RuntimeException("Firebase initialization failed due to unexpected error", e)
        }
    }
}