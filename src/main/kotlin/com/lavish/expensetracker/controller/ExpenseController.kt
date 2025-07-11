package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.exception.ExpenseValidationException
import com.lavish.expensetracker.exception.ExpenseCreationException
import com.lavish.expensetracker.exception.ExpenseNotFoundException
import com.lavish.expensetracker.exception.ExpenseAccessDeniedException
import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.model.PagedResponse
import com.lavish.expensetracker.service.ExpenseService
import com.lavish.expensetracker.service.UserService
import com.lavish.expensetracker.service.UserDeviceService
import com.lavish.expensetracker.util.AuthUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/expenses")
class ExpenseController(
    private val expenseService: ExpenseService,
    private val authUtil: AuthUtil,
    @Autowired private val pushNotificationService: PushNotificationService,
    private val userService: UserService,
    private val userDeviceService: UserDeviceService
) {
    private val logger = LoggerFactory.getLogger(ExpenseController::class.java)

    /**
     * Get all expenses for the current user with pagination and sorting.
     * Supports cursor-based pagination using expense ID to prevent duplicates with dynamic page sizes.
     */
    @GetMapping
    fun getExpenses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?, // Cursor-based pagination
        @RequestParam(defaultValue = "date") sortBy: String,
        @RequestParam(defaultValue = "false") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        logger.info("Getting personal expenses for user: $currentUserId, page: $page, size: $size, sortBy: $sortBy, isAsc: $isAsc")

        // Validate pagination parameters
        val validatedSize = when {
            size <= 0 -> 10 // Default to 10 if size is 0 or negative
            size > 100 -> 100 // Cap at 100 to prevent performance issues
            else -> size
        }

        // Validate sortBy parameter to prevent SQL injection
        val validSortFields = listOf(
            "expenseCreatedOn", "lastModifiedOn", "amount", "category",
            "description", "date", "userId", "expenseId"
        )

        val safeSortBy = if (validSortFields.contains(sortBy)) sortBy else "date"

        return if (lastExpenseId != null) {
            logger.debug("Using cursor-based pagination with lastExpenseId: $lastExpenseId")
            // Use cursor-based pagination for personal expenses only
            expenseService.getPersonalExpensesByUserIdAfterCursor(
                currentUserId, lastExpenseId, validatedSize, safeSortBy, isAsc
            )
        } else {
            logger.debug("Using traditional page-based pagination")
            // Use traditional page-based pagination for first page - personal expenses only
            val validatedPage = maxOf(0, page)
            expenseService.getPersonalExpensesByUserIdWithOrder(
                currentUserId,
                validatedPage,
                validatedSize,
                safeSortBy,
                isAsc
            )
        }
    }

    @GetMapping("/family")
    fun getExpensesForFamily(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?, // Cursor-based pagination
        @RequestParam(defaultValue = "date") sortBy: String,
        @RequestParam(defaultValue = "false") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        logger.info("Getting family expenses for user: $currentUserId, page: $page, size: $size, sortBy: $sortBy, isAsc: $isAsc")

        // Get the user's family ID
        val user = userService.findById(currentUserId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found")

        val familyId = user.familyId
        if (familyId.isNullOrBlank()) {
            logger.warn("User $currentUserId is not part of any family, returning empty family expenses")
            // Return an empty result if user is not part of any family
            return PagedResponse(
                content = emptyList(),
                page = maxOf(0, page),
                size = when {
                    size <= 0 -> 10
                    size > 100 -> 100
                    else -> size
                },
                totalElements = 0,
                totalPages = 0,
                isFirst = true,
                isLast = true,
                hasNext = false,
                hasPrevious = false
            )
        }

        logger.debug("User $currentUserId belongs to family: $familyId")

        // Validate pagination parameters
        val validatedSize = when {
            size <= 0 -> 10 // Default to 10 if size is 0 or negative
            size > 100 -> 100 // Cap at 100 to prevent performance issues
            else -> size
        }

        // Validate sortBy parameter to prevent SQL injection
        val validSortFields = listOf(
            "expenseCreatedOn", "lastModifiedOn", "amount", "category",
            "description", "date", "userId", "expenseId"
        )

        val safeSortBy = if (validSortFields.contains(sortBy)) sortBy else "date"

        return if (lastExpenseId != null) {
            logger.debug("Using cursor-based pagination for family expenses with lastExpenseId: $lastExpenseId")
            // Use cursor-based pagination for family expenses (both familyId and userId based)
            expenseService.getExpensesByFamilyIdAndUserFamilyAfterCursor(
                familyId, lastExpenseId, validatedSize, safeSortBy, isAsc
            )
        } else {
            logger.debug("Using traditional page-based pagination for family expenses")
            // Use traditional page-based pagination for first page
            val validatedPage = maxOf(0, page)
            expenseService.getExpensesByFamilyIdAndUserFamilyWithOrder(
                familyId,
                validatedPage,
                validatedSize,
                safeSortBy,
                isAsc
            )
        }.also {
            logger.info("Successfully retrieved ${it.content.size} family expenses for user: $currentUserId, family: $familyId")
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(@RequestBody expense: ExpenseDto): ResponseEntity<Any> {
        logger.info("Creating expense request received: amount=${expense.amount}, category=${expense.category}, description=${expense.description}")

        return try {
            val currentUserId = try {
                authUtil.getCurrentUserId()
            } catch (e: ResponseStatusException) {
                logger.warn("Authentication/Authorization failed during expense creation: ${e.message}")
                return when (e.statusCode) {
                    HttpStatus.UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        mapOf(
                            "success" to false,
                            "message" to "Authentication required. Please provide a valid JWT token."
                        )
                    )

                    HttpStatus.FORBIDDEN -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        mapOf(
                            "success" to false,
                            "message" to "User account not found or has been deactivated. Please re-authenticate."
                        )
                    )

                    else -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        mapOf(
                            "success" to false,
                            "message" to "Authentication failed: ${e.reason}"
                        )
                    )
                }
            }

            logger.debug("Current user ID: $currentUserId")

            // Check if user exists before proceeding (additional check)
            logger.debug("Validating user existence for ID: $currentUserId")
            val user = userService.findById(currentUserId)
            if (user == null) {
                logger.warn("User not found for ID: $currentUserId - returning 400 Bad Request")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    mapOf(
                        "success" to false,
                        "message" to "User not found"
                    )
                )
            }
            logger.debug("User validation successful for ID: $currentUserId, user name: ${user.name}")

            // Validate the expense data
            logger.debug("Validating expense data")
            val validationErrors = validateExpense(expense)
            if (validationErrors.isNotEmpty()) {
                logger.warn("Expense validation failed with errors: $validationErrors")
                throw ExpenseValidationException(
                    "Expense validation failed",
                    validationErrors
                )
            }
            logger.debug("Expense validation successful")

            val expenseWithUser = expense.copy(
                userId = currentUserId,
                createdBy = currentUserId,
                modifiedBy = currentUserId,
                expenseCreatedOn = System.currentTimeMillis(),
                lastModifiedOn = System.currentTimeMillis()
            )

            logger.debug("Attempting to save expense to database")
            val createdExpense = try {
                expenseService.createExpense(expenseWithUser)
            } catch (e: Exception) {
                logger.error("Failed to save expense to database: ${e.message}", e)
                throw ExpenseCreationException(
                    "Failed to save expense to database: ${e.message}",
                    e
                )
            }
            logger.info("Successfully created expense with ID: ${createdExpense.expenseId}")

            // Send FCM notification to all user devices after creating expense
            logger.debug("Attempting to send FCM notifications")
            try {
                val fcmTokens: List<String> =
                    userService.getFamilyMembersFcmTokens(currentUserId).flatMap { userService.getAllFcmTokens(it.id) }.distinct()
                logger.debug("Found ${fcmTokens.size} FCM tokens for user: $currentUserId")
                if (fcmTokens.isNotEmpty()) {
                    val formattedAmount = "$${expense.amount}"
                    val invalidTokens = pushNotificationService.sendExpenseNotificationToMultiple(
                        fcmTokens, formattedAmount, expense.description, user.name
                    )
                    logger.info("FCM notifications sent successfully. Invalid tokens: ${invalidTokens.size}")
                    // Clean up invalid tokens
                    if (invalidTokens.isNotEmpty()) {
                        logger.debug("Cleaning up ${invalidTokens.size} invalid FCM tokens")
                        userDeviceService.removeInvalidTokens(invalidTokens)
                    }
                } else {
                    logger.debug("No FCM tokens found for user: $currentUserId")
                }
            } catch (e: Exception) {
                // Log notification error but don't fail the expense creation
                logger.error(
                    "Failed to send push notification for expense ${createdExpense.expenseId}: ${e.message}",
                    e
                )
            }

            logger.info("Expense creation completed successfully for user: $currentUserId, expenseId: ${createdExpense.expenseId}")
            ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf(
                    "success" to true,
                    "message" to "Expense created successfully",
                    "expense" to createdExpense
                )
            )

        } catch (e: ExpenseValidationException) {
            logger.error("Expense validation exception: ${e.message}")
            // Re-throw validation exceptions to be handled by GlobalExceptionHandler
            throw e
        } catch (e: ExpenseCreationException) {
            logger.error("Expense creation exception: ${e.message}", e)
            // Re-throw creation exceptions to be handled by GlobalExceptionHandler
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error occurred while creating expense: ${e.message}", e)
            // Catch any unexpected exceptions
            throw ExpenseCreationException(
                "An unexpected error occurred while creating the expense: ${e.message}",
                e
            )
        }
    }

    private fun validateExpense(expense: ExpenseDto): List<String> {
        logger.debug("Starting expense validation for amount: ${expense.amount}, category: ${expense.category}")
        val errors = mutableListOf<String>()

        // Validate amount
        if (expense.amount <= 0) {
            errors.add("Amount is required and must be greater than 0")
        }
        if (expense.amount > 1000000) {
            errors.add("Amount cannot exceed $1,000,000")
        }
        if (expense.amount.toString().length > 10) {
            errors.add("Amount value is too large")
        }

        // Validate category
        val validCategories = listOf(
            "FOOD",
            "ENTERTAINMENT",
            "FUN",
            "BILLS",
            "TRAVEL",
            "UTILITIES",
            "HEALTH",
            "SHOPPING",
            "EDUCATION",
            "OTHERS"
        )
        if (expense.category.isBlank()) {
            errors.add("Category is required")
        } else if (!validCategories.contains(expense.category.uppercase())) {
            errors.add("Category must be one of: ${validCategories.joinToString(", ")}")
        }

        // Validate description (optional but if provided, should have reasonable length)
        if (expense.description.length > 500) {
            errors.add("Description cannot exceed 500 characters")
        }

        // Check for potentially harmful content in description
        val suspiciousPatterns = listOf("<script", "javascript:", "onerror=", "onload=")
        if (suspiciousPatterns.any { expense.description.lowercase().contains(it) }) {
            errors.add("Description contains invalid characters")
        }

        // Validate date
        if (expense.date <= 0) {
            errors.add("Date is required and must be a valid timestamp")
        }

        // Check if the date is not too far in the future (more than 1 day)
        val oneDayFromNow = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        if (expense.date > oneDayFromNow) {
            errors.add("Date cannot be more than 1 day in the future")
        }

        // Check if date is not too far in the past (more than 10 years)
        val tenYearsAgo = System.currentTimeMillis() - (10 * 365 * 24 * 60 * 60 * 1000L)
        if (expense.date < tenYearsAgo) {
            errors.add("Date cannot be more than 10 years in the past")
        }

        // Validate user ID format if provided
        if (expense.userId.isNotBlank() && expense.userId.length < 3) {
            errors.add("User ID format is invalid")
        }

        // Validate family ID if provided
        if (expense.familyId?.isNotBlank() == true && expense.familyId.length < 3) {
            errors.add("Family ID format is invalid")
        }

        logger.debug("Expense validation completed with ${errors.size} errors")
        return errors
    }

    @GetMapping("/detail/{id}")
    fun getExpenseById(@PathVariable id: String): ResponseEntity<ExpenseDto> {
        logger.info("Getting expense by ID: $id")
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            logger.debug("Current user ID: $currentUserId")

            val expense = expenseService.getExpenseById(id)
                ?: throw ExpenseNotFoundException("Expense with ID '$id' not found")

            logger.debug("Found expense with ID: $id, userId: ${expense.userId}")
            if (expense.userId != currentUserId) {
                logger.warn("Access denied for user $currentUserId trying to access expense $id owned by ${expense.userId}")
                throw ExpenseAccessDeniedException("You don't have permission to view this expense")
            }

            logger.info("Successfully retrieved expense $id for user $currentUserId")
            ResponseEntity.ok(expense)
        } catch (e: ExpenseNotFoundException) {
            logger.error("Expense not found: ${e.message}")
            throw e
        } catch (e: ExpenseAccessDeniedException) {
            logger.error("Access denied: ${e.message}")
            throw e
        } catch (e: Exception) {
            logger.error("Error retrieving expense $id: ${e.message}", e)
            throw ExpenseCreationException("Error retrieving expense: ${e.message}", e)
        }
    }

    @PutMapping("/{id}")
    fun updateExpense(@PathVariable id: String, @RequestBody expense: ExpenseDto): ResponseEntity<ExpenseDto> {
        logger.info("Updating expense with ID: $id")
        val currentUserId = authUtil.getCurrentUserId()
        logger.debug("Current user ID: $currentUserId")

        return try {
            val existingExpense = expenseService.getExpenseById(id)
            if (existingExpense?.userId != currentUserId) {
                logger.warn("Access denied for user $currentUserId trying to update expense $id")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }

            logger.debug("Updating expense $id with new data")
            val updatedExpense = expenseService.updateExpense(
                id, expense.copy(
                    userId = currentUserId,
                    modifiedBy = currentUserId
                )
            )
            logger.info("Successfully updated expense $id for user $currentUserId")
            ResponseEntity.ok(updatedExpense)
        } catch (e: NoSuchElementException) {
            logger.error("Expense not found for update: $id")
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            logger.error("Error updating expense $id: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteExpense(@PathVariable id: String): ResponseEntity<Void> {
        logger.info("Deleting expense with ID: $id")
        val currentUserId = authUtil.getCurrentUserId()
        logger.debug("Current user ID: $currentUserId")

        val existingExpense = expenseService.getExpenseById(id)

        if (existingExpense?.userId != currentUserId) {
            logger.warn("Access denied for user $currentUserId trying to delete expense $id")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return if (expenseService.deleteExpense(id)) {
            logger.info("Successfully deleted expense $id for user $currentUserId")
            ResponseEntity.noContent().build()
        } else {
            logger.error("Failed to delete expense $id - not found")
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/category/{category}")
    fun getExpensesByCategory(
        @PathVariable category: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()

        // Validate pagination parameters
        val validatedPage = maxOf(0, page) // Ensure page is not negative
        val validatedSize = when {
            size <= 0 -> 10 // Default to 10 if size is 0 or negative
            size > 100 -> 100 // Cap at 100 to prevent performance issues
            else -> size
        }

        return expenseService.getExpensesByUserIdAndCategory(currentUserId, category, validatedPage, validatedSize)
    }

    @GetMapping("/between-dates")
    fun getExpensesBetweenDates(
        @RequestParam startDate: String,
        @RequestParam endDate: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()

        // Validate pagination parameters
        val validatedPage = maxOf(0, page) // Ensure page is not negative
        val validatedSize = when {
            size <= 0 -> 10 // Default to 10 if size is 0 or negative
            size > 100 -> 100 // Cap at 100 to prevent performance issues
            else -> size
        }

        val start = LocalDate.parse(startDate).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
        val end = LocalDate.parse(endDate).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
        return expenseService.getExpensesByUserIdAndDateRange(currentUserId, start, end, validatedPage, validatedSize)
    }

    // Simplified data class - now only needs expenseId since token is retrieved from DB
    data class ExpenseNotificationRequest(val expenseId: String)

    @PostMapping("/notify")
    fun notifyExpense(@RequestBody request: ExpenseNotificationRequest): ResponseEntity<String> {
        val currentUserId = authUtil.getCurrentUserId()

        // Get the expense details
        val expense = expenseService.getExpenseById(request.expenseId)
        if (expense == null || expense.userId != currentUserId) {
            return ResponseEntity.badRequest().body("Expense not found or access denied")
        }

        // Get all user's FCM tokens from database
        val fcmTokens = userService.getAllFcmTokens(currentUserId)
        if (fcmTokens.isEmpty()) {
            return ResponseEntity.badRequest().body("No FCM tokens found. Please update your device token first.")
        }

        // Send notification to all devices
        val title = "Expense Notification"
        val body = "Expense '${expense.description}' of $${expense.amount}"
        val invalidTokens = pushNotificationService.sendNotificationToMultiple(fcmTokens, title, body)

        // Clean up invalid tokens
        if (invalidTokens.isNotEmpty()) {
            userDeviceService.removeInvalidTokens(invalidTokens)
        }

        return ResponseEntity.ok("Notification sent successfully to ${fcmTokens.size - invalidTokens.size} device(s)")
    }

    @GetMapping("/monthly-sum")
    fun getMonthlyExpenseSum(
        @RequestParam year: Int,
        @RequestParam month: Int
    ): ResponseEntity<Map<String, Any>> {
        val currentUserId = authUtil.getCurrentUserId()

        // Validate month parameter
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "Invalid month. Month must be between 1 and 12",
                    "month" to month
                )
            )
        }

        val totalAmount = expenseService.getMonthlyExpenseSum(currentUserId, year, month)

        return ResponseEntity.ok(
            mapOf(
                "year" to year,
                "month" to month,
                "totalAmount" to totalAmount,
                "userId" to currentUserId
            )
        )
    }

    @GetMapping("/since")
    fun getExpensesSince(
        @RequestParam lastModified: Long, // Timestamp in milliseconds
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?, // For cursor-based pagination
        @RequestParam(defaultValue = "lastModifiedOn") sortBy: String,
        @RequestParam(defaultValue = "true") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()

        // Validate pagination parameters
        val validatedSize = when {
            size <= 0 -> 10 // Default to 10 if size is 0 or negative
            size > 100 -> 100 // Cap at 100 to prevent performance issues
            else -> size
        }

        // Validate sortBy parameter for sync operations
        val validSortFields = listOf(
            "lastModifiedOn", "expenseCreatedOn", "date"
        )
        val safeSortBy = if (validSortFields.contains(sortBy)) sortBy else "lastModifiedOn"

        return if (lastExpenseId != null) {
            // Use cursor-based pagination for subsequent requests
            expenseService.getExpensesSinceWithCursor(
                currentUserId, lastModified, lastExpenseId, validatedSize, safeSortBy, isAsc
            )
        } else {
            // Initial request without cursor
            expenseService.getExpensesSince(
                currentUserId, lastModified, validatedSize, safeSortBy, isAsc
            )
        }
    }

    @GetMapping("/since-date")
    fun getExpensesSinceDate(
        @RequestParam date: String, // Date in YYYY-MM-DD format
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?,
        @RequestParam(defaultValue = "date") sortBy: String,
        @RequestParam(defaultValue = "true") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()

        // Validate pagination parameters
        val validatedSize = when {
            size <= 0 -> 10
            size > 100 -> 100
            else -> size
        }

        // Parse date and convert to timestamp
        val sinceTimestamp = try {
            LocalDate.parse(date).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
        } catch (e: Exception) {
            throw ExpenseValidationException(
                "Invalid date format. Use YYYY-MM-DD format",
                listOf("Date parsing error: ${e.message}")
            )
        }

        // Validate sortBy parameter
        val validSortFields = listOf(
            "date", "lastModifiedOn", "expenseCreatedOn", "amount"
        )
        val safeSortBy = if (validSortFields.contains(sortBy)) sortBy else "date"

        return if (lastExpenseId != null) {
            expenseService.getExpensesSinceDateWithCursor(
                currentUserId, sinceTimestamp, lastExpenseId, validatedSize, safeSortBy, isAsc
            )
        } else {
            expenseService.getExpensesSinceDate(
                currentUserId, sinceTimestamp, validatedSize, safeSortBy, isAsc
            )
        }
    }
}
