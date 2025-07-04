package com.example.expensetracker.config

import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.io.File

@Component
class ApplicationFailureHandler : ApplicationListener<ApplicationFailedEvent> {

    private val logger = LoggerFactory.getLogger(ApplicationFailureHandler::class.java)

    override fun onApplicationEvent(event: ApplicationFailedEvent) {
        val exception = event.exception
        logger.error("Application failed to start: ${exception.message}", exception)

        // Clean up database locks on failure
        if (exception.message?.contains("database", ignoreCase = true) == true ||
            exception.message?.contains("locked", ignoreCase = true) == true) {
            cleanupDatabaseLocks()
        }

        // Log helpful recovery steps
        logger.info("Recovery suggestions:")
        logger.info("1. Check if another instance is running: lsof -i :8080")
        logger.info("2. Clean database locks: ./cleanup.sh")
        logger.info("3. Restart application: ./start.sh")
    }

    private fun cleanupDatabaseLocks() {
        try {
            val dataDir = File("data/h2")
            if (dataDir.exists()) {
                dataDir.listFiles { file -> file.name.endsWith(".lock.db") }
                    ?.forEach { lockFile ->
                        if (lockFile.delete()) {
                            logger.info("Deleted lock file: ${lockFile.name}")
                        }
                    }
            }
        } catch (e: Exception) {
            logger.warn("Failed to clean up database locks: ${e.message}")
        }
    }
}
