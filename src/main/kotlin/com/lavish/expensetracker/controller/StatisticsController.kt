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
        @RequestParam(name = "period", defaultValue = "month") period: String, // kept for backwards compat but only "month" recognized
        @RequestParam(name = "start_date", required = false) startDate: String?, // format YYYY-MM-DD
        @RequestParam(name = "end_date", required = false) endDate: String?      // format YYYY-MM-DD
    ): ResponseEntity<Any> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            val currentUser = userService.findById(currentUserId)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "User not found"))

            if (currentUserId != userId) {
                if (currentUser.familyId.isNullOrBlank() ||
                    currentUser.familyId != userService.findById(userId)?.familyId) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(mapOf("error" to "Access denied to user statistics"))
                }
            }

            // Derive date range. If both dates provided use them, else default to current month
            val (fromMillis, toMillis) = resolveDateRange(startDate, endDate)

            val userStats = statisticsService.getUserStatsInRange(userId, fromMillis, toMillis)
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
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to retrieve statistics: ${e.message}"))
        }
    }

    @GetMapping("/family/{familyId}")
    fun getFamilyStats(
        @PathVariable familyId: String,
        @RequestParam(name = "period", defaultValue = "month") period: String, // kept for compatibility
        @RequestParam(name = "start_date", required = false) startDate: String?,
        @RequestParam(name = "end_date", required = false) endDate: String?
    ): ResponseEntity<Any> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            val currentUser = userService.findById(currentUserId)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "User not found"))

            if (currentUser.familyId != familyId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "Access denied to family statistics"))
            }

            val (fromMillis, toMillis) = resolveDateRange(startDate, endDate)
            val familyStats = statisticsService.getFamilyStatsInRange(familyId, fromMillis, toMillis)
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
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to retrieve family statistics: ${e.message}"))
        }
    }

    @GetMapping("/personal/{userId}/monthly-trend")
    fun getPersonalMonthlyTrend(
        @PathVariable userId: String
    ): ResponseEntity<Any> {
        return try {
            // Get current authenticated user
            val currentUserId = authUtil.getCurrentUserId()
            val currentUser = userService.findById(currentUserId)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "User not found"))

            // Validate that user can access the requested userId monthly trend
            if (currentUserId != userId) {
                // Check if they are in the same family
                if (currentUser.familyId.isNullOrBlank() || 
                    currentUser.familyId != userService.findById(userId)?.familyId) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(mapOf("error" to "Access denied to user monthly trend"))
                }
            }

            val requestedUser = userService.findById(userId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Requested user not found"))

            val monthlyTrend = statisticsService.getUserMonthlyTrend(userId)
            ResponseEntity.ok(mapOf("monthlyTrend" to monthlyTrend))

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
                .body(mapOf("error" to "Failed to retrieve personal monthly trend: ${e.message}"))
        }
    }

    @GetMapping("/family/{familyId}/monthly-trend")
    fun getFamilyMonthlyTrend(
        @PathVariable familyId: String
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
                    .body(mapOf("error" to "Access denied to family monthly trend"))
            }

            val monthlyTrend = statisticsService.getFamilyMonthlyTrend(familyId)
            ResponseEntity.ok(mapOf("monthlyTrend" to monthlyTrend))

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
                .body(mapOf("error" to "Failed to retrieve family monthly trend: ${e.message}"))
        }
    }

    // Helper to resolve date range (inclusive entire days) defaulting to current month when nulls provided
    private fun resolveDateRange(start: String?, end: String?): Pair<Long, Long> {
        val zone = java.time.ZoneOffset.UTC
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        return if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
            val startDate = java.time.LocalDate.parse(start, formatter)
            val endDate = java.time.LocalDate.parse(end, formatter)
            if (endDate.isBefore(startDate)) throw IllegalArgumentException("end_date cannot be before start_date")
            val startMillis = startDate.atStartOfDay().toEpochSecond(zone) * 1000
            val endMillis = endDate.atTime(23,59,59).toEpochSecond(zone) * 1000
            Pair(startMillis, endMillis)
        } else {
            val now = java.time.LocalDate.now()
            val first = now.withDayOfMonth(1)
            val last = now.withDayOfMonth(now.lengthOfMonth())
            val startMillis = first.atStartOfDay().toEpochSecond(zone) * 1000
            val endMillis = last.atTime(23,59,59).toEpochSecond(zone) * 1000
            Pair(startMillis, endMillis)
        }
    }
}
