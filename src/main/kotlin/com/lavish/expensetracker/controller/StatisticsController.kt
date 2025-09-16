package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.StatisticsService
import com.lavish.expensetracker.service.UserService
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/stats")
class StatisticsController(
    private val statisticsService: StatisticsService,
    private val userService: UserService,
    private val authUtil: AuthUtil
) {

    @GetMapping("/personal/{userId}")
    fun getPersonalStats(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "current_month") period: String
    ): ResponseEntity<Any> {
        return try {
            // Get current authenticated user
            val currentUserId = authUtil.getCurrentUserId()
            val currentUser = userService.findById(currentUserId)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "User not found"))

            // Validate that user can access the requested userId stats
            if (currentUserId != userId) {
                // Check if they are in the same family
                if (currentUser.familyId.isNullOrBlank() || 
                    currentUser.familyId != userService.findById(userId)?.familyId) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(mapOf("error" to "Access denied to user statistics"))
                }
            }

            val requestedUser = userService.findById(userId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Requested user not found"))

            val parsedPeriod = StatisticsService.parsePeriod(period)

            // Check if user is part of a family
            if (!requestedUser.familyId.isNullOrBlank()) {
                // Return family stats
                val familyStats = statisticsService.getFamilyStats(requestedUser.familyId)
                ResponseEntity.ok(familyStats)
            } else {
                // Return personal stats
                val userStats = statisticsService.getUserStats(userId, parsedPeriod)
                ResponseEntity.ok(userStats)
            }

        } catch (e: ResponseStatusException) {
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Authentication required. Please provide a valid JWT token."))
                HttpStatus.FORBIDDEN -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Please re-authenticate."))
                else -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Authentication failed: ${e.reason}"))
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to retrieve statistics: ${e.message}"))
        }
    }

    @GetMapping("/family/{familyId}")
    fun getFamilyStats(
        @PathVariable familyId: String,
        @RequestParam(defaultValue = "current_month") period: String
    ): ResponseEntity<Any> {
        return try {
            // Get current authenticated user
            val currentUserId = authUtil.getCurrentUserId()
            val currentUser = userService.findById(currentUserId)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "User not found"))

            // Validate that user is part of the requested family
            if (currentUser.familyId != familyId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "Access denied to family statistics"))
            }

            val familyStats = statisticsService.getFamilyStats(familyId)
            ResponseEntity.ok(familyStats)

        } catch (e: ResponseStatusException) {
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Authentication required. Please provide a valid JWT token."))
                HttpStatus.FORBIDDEN -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Please re-authenticate."))
                else -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Authentication failed: ${e.reason}"))
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to retrieve family statistics: ${e.message}"))
        }
    }

    @GetMapping("/user/{userId}")
    fun getUserStats(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "current_month") period: String
    ): ResponseEntity<Any> {
        return try {
            // Get current authenticated user
            val currentUserId = authUtil.getCurrentUserId()
            val currentUser = userService.findById(currentUserId)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "User not found"))

            // Validate that user can access the requested userId stats
            if (currentUserId != userId) {
                // Check if they are in the same family
                if (currentUser.familyId.isNullOrBlank() || 
                    currentUser.familyId != userService.findById(userId)?.familyId) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(mapOf("error" to "Access denied to user statistics"))
                }
            }

            val parsedPeriod = StatisticsService.parsePeriod(period)
            val userStats = statisticsService.getUserStats(userId, parsedPeriod)
            ResponseEntity.ok(userStats)

        } catch (e: ResponseStatusException) {
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Authentication required. Please provide a valid JWT token."))
                HttpStatus.FORBIDDEN -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Please re-authenticate."))
                else -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Authentication failed: ${e.reason}"))
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to retrieve user statistics: ${e.message}"))
        }
    }
}
