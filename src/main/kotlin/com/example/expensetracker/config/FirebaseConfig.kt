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
            val serviceAccountResource = ClassPathResource("serviceAccountKey.json")

            if (!serviceAccountResource.exists()) {
                val errorMsg = "Firebase service account file not found! Make sure serviceAccountKey.json is in the resources directory."
                logger.error(errorMsg)
                throw IllegalStateException(errorMsg)
            }

            val credentials = GoogleCredentials.fromStream(serviceAccountResource.inputStream)
            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()

            return if (FirebaseApp.getApps().isEmpty()) {
                logger.info("Initializing Firebase application")
                FirebaseApp.initializeApp(options)
            } else {
                logger.info("Firebase application already initialized")
                FirebaseApp.getInstance()
            }
        } catch (e: IOException) {
            val errorMsg = "Error initializing Firebase: ${e.message}"
            logger.error(errorMsg, e)
            throw IllegalStateException(errorMsg, e)
        }
    }
}
