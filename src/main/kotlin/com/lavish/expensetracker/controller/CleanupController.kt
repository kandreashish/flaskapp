package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.FamilyCleanupService
import com.lavish.expensetracker.util.ApiResponseUtil
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/cleanup")
class CleanupController(
    private val familyCleanupService: FamilyCleanupService
) {

    private val logger = LoggerFactory.getLogger(CleanupController::class.java)

    @PostMapping("/orphaned-families")
    fun cleanupOrphanedFamilyReferences(): ResponseEntity<*> {
        logger.info("Manual cleanup of orphaned family references requested")

        return try {
            val cleanedUpCount = familyCleanupService.performManualCleanup()

            val response = mapOf(
                "message" to "Cleanup completed successfully",
                "cleanedUpUsers" to cleanedUpCount
            )

            ResponseEntity.ok(response)
        } catch (exception: Exception) {
            logger.error("Error during manual cleanup", exception)
            ApiResponseUtil.internalServerError("Failed to perform cleanup")
        }
    }
}
