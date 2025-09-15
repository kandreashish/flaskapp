package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.UserStats
import com.lavish.expensetracker.service.StatisticsService
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/stats")
class StatisticsController(
    private val statisticsService: StatisticsService,
    private val authUtil: AuthUtil
) {

    @GetMapping("/personal/{userId}")
    fun getPersonalStats(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "current_month") period: String
    ): ResponseEntity<UserStats> {
        return try {
            // Get current authenticated user
            val currentUserId = authUtil.getCurrentUserId()
            
            // Verify the user can only access their own stats (for security)
            if (currentUserId != userId) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only access your own statistics"
                )
            }

            val stats = statisticsService.getPersonalStats(userId, period)
            ResponseEntity.ok(stats)
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to retrieve statistics: ${e.message}"
            )
        }
    }
}