package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.ExpenseCleanupService
import com.lavish.expensetracker.util.AuthUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/cleanup")
@Tag(name = "Admin Cleanup", description = "Administrative endpoints for managing expense cleanup")
@SecurityRequirement(name = "Bearer Authentication")
class AdminCleanupController(
    private val expenseCleanupService: ExpenseCleanupService,
    private val authUtil: AuthUtil
) {
    private val logger = LoggerFactory.getLogger(AdminCleanupController::class.java)

    @GetMapping("/statistics")
    @Operation(summary = "Get cleanup statistics", description = "Returns statistics about soft-deleted expenses")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            ApiResponse(responseCode = "403", description = "Access denied - admin privileges required")
        ]
    )
    @PreAuthorize("hasRole('ADMIN')") // Requires admin role
    fun getCleanupStatistics(): ResponseEntity<Map<String, Any>> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            logger.info("Admin user $currentUserId requested cleanup statistics")

            val statistics = expenseCleanupService.getCleanupStatistics()
            ResponseEntity.ok(statistics)
        } catch (e: Exception) {
            logger.error("Error retrieving cleanup statistics: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                mapOf("error" to "Failed to retrieve cleanup statistics")
            )
        }
    }

    @PostMapping("/manual")
    @Operation(summary = "Trigger manual cleanup", description = "Manually trigger cleanup of old soft-deleted expenses")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Cleanup completed successfully"),
            ApiResponse(responseCode = "403", description = "Access denied - admin privileges required")
        ]
    )
    @PreAuthorize("hasRole('ADMIN')")
    fun triggerManualCleanup(
        @RequestParam(defaultValue = "90") daysOld: Long
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            logger.info("Admin user $currentUserId triggered manual cleanup for expenses older than $daysOld days")

            val deletedCount = expenseCleanupService.manualCleanup(daysOld)

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Manual cleanup completed successfully",
                    "deletedCount" to deletedCount,
                    "daysOld" to daysOld
                )
            )
        } catch (e: Exception) {
            logger.error("Error during manual cleanup: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "error" to "Manual cleanup failed: ${e.message}"
                )
            )
        }
    }
}
