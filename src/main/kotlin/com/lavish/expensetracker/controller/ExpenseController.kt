package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.exception.ExpenseAccessDeniedException
import com.lavish.expensetracker.exception.ExpenseCreationException
import com.lavish.expensetracker.exception.ExpenseNotFoundException
import com.lavish.expensetracker.exception.ExpenseValidationException
import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.model.PagedResponse
import com.lavish.expensetracker.service.ExpenseService
import com.lavish.expensetracker.service.UserDeviceService
import com.lavish.expensetracker.service.UserService
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

    companion object {
        private val VALID_SORT_FIELDS = listOf(
            "expenseCreatedOn", "lastModifiedOn", "amount", "category",
            "description", "date", "userId", "expenseId"
        )
        private val VALID_SYNC_SORT_FIELDS = listOf(
            "lastModifiedOn", "expenseCreatedOn", "date"
        )
        private val VALID_CATEGORIES = listOf(
            "FOOD", "ENTERTAINMENT", "FUN", "BILLS", "TRAVEL",
            "UTILITIES", "HEALTH", "SHOPPING", "EDUCATION", "OTHERS"
        )
        private const val DEFAULT_PAGE_SIZE = 10
        private const val MAX_PAGE_SIZE = 100
        private const val MAX_AMOUNT = 1000000.0
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val MAX_AMOUNT_STRING_LENGTH = 10
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val TEN_YEARS_MILLIS = 10 * 365 * 24 * 60 * 60 * 1000L
    }

    // Data classes for common operations
    data class PaginationParams(
        val page: Int,
        val size: Int,
        val lastExpenseId: String?,
        val sortBy: String,
        val isAsc: Boolean
    )

    data class ValidatedPaginationParams(
        val page: Int,
        val size: Int,
        val lastExpenseId: String?,
        val sortBy: String,
        val isAsc: Boolean
    )

    data class ExpenseNotificationRequest(val expenseId: String)

    // Common validation and utility methods
    private fun getCurrentUserWithValidation(): ExpenseUser {
        val currentUserId = try {
            authUtil.getCurrentUserId()
        } catch (e: ResponseStatusException) {
            logger.warn("Authentication/Authorization failed: ${e.message}")
            throw when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authentication required. Please provide a valid JWT token."
                )
                HttpStatus.FORBIDDEN -> ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "ExpenseUser account not found or has been deactivated. Please re-authenticate."
                )
                else -> ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Authentication failed: ${e.reason}"
                )
            }
        }

        return userService.findById(currentUserId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ExpenseUser not found")
    }

    private fun validatePaginationParams(
        page: Int,
        size: Int,
        lastExpenseId: String?,
        sortBy: String,
        isAsc: Boolean,
        validSortFields: List<String> = VALID_SORT_FIELDS
    ): ValidatedPaginationParams {
        val validatedPage = maxOf(0, page)
        val validatedSize = when {
            size <= 0 -> DEFAULT_PAGE_SIZE
            size > MAX_PAGE_SIZE -> MAX_PAGE_SIZE
            else -> size
        }
        val safeSortBy = if (validSortFields.contains(sortBy)) sortBy else "date"

        return ValidatedPaginationParams(validatedPage, validatedSize, lastExpenseId, safeSortBy, isAsc)
    }

    private fun validateExpenseData(expense: ExpenseDto): List<String> {
        logger.debug("Starting expense validation for amount: ${expense.amount}, category: ${expense.category}")
        val errors = mutableListOf<String>()

        // Validate amount
        when {
            expense.amount <= 0 -> errors.add("Amount is required and must be greater than 0")
            expense.amount > MAX_AMOUNT -> errors.add("Amount cannot exceed $${MAX_AMOUNT.toInt()}")
            expense.amount.toString().length > MAX_AMOUNT_STRING_LENGTH -> errors.add("Amount value is too large")
        }

        // Validate category
        when {
            expense.category.isBlank() -> errors.add("Category is required")
            !VALID_CATEGORIES.contains(expense.category.uppercase()) ->
                errors.add("Category must be one of: ${VALID_CATEGORIES.joinToString(", ")}")
        }

        // Validate description
        if (expense.description.length > MAX_DESCRIPTION_LENGTH) {
            errors.add("Description cannot exceed $MAX_DESCRIPTION_LENGTH characters")
        }

        // Check for potentially harmful content in description
        val suspiciousPatterns = listOf("<script", "javascript:", "onerror=", "onload=")
        if (suspiciousPatterns.any { expense.description.lowercase().contains(it) }) {
            errors.add("Description contains invalid characters")
        }

        // Validate date
        val currentTime = System.currentTimeMillis()
        when {
            expense.date <= 0 -> errors.add("Date is required and must be a valid timestamp")
            expense.date > currentTime + ONE_DAY_MILLIS ->
                errors.add("Date cannot be more than 1 day in the future")
            expense.date < currentTime - TEN_YEARS_MILLIS ->
                errors.add("Date cannot be more than 10 years in the past")
        }

        // Validate user ID format if provided
        if (expense.userId.isNotBlank() && expense.userId.length < 3) {
            errors.add("ExpenseUser ID format is invalid")
        }

        // Validate family ID if provided
        if (expense.familyId?.isNotBlank() == true && expense.familyId.length < 3) {
            errors.add("Family ID format is invalid")
        }

        logger.debug("Expense validation completed with ${errors.size} errors")
        return errors
    }

    private fun validateExpenseAccess(expense: ExpenseDto?, currentUserId: String, expenseId: String): ExpenseDto {
        if (expense == null) {
            throw ExpenseNotFoundException("Expense with ID '$expenseId' not found")
        }

        if (expense.userId != currentUserId) {
            logger.warn("Access denied for user $currentUserId trying to access expense $expenseId owned by ${expense.userId}")
            throw ExpenseAccessDeniedException("You don't have permission to view this expense")
        }

        return expense
    }

    private fun canDeleteExpense(expense: ExpenseDto, currentUser: ExpenseUser): Boolean {
        val expenseOwner = userService.findById(expense.userId)
            ?: return false

        return expense.userId == currentUser.id ||
                (currentUser.familyId != null &&
                        currentUser.familyId == expenseOwner.familyId &&
                        currentUser.familyId.isNotBlank())
    }

    private fun sendExpenseNotification(expense: ExpenseDto, user: ExpenseUser, amount: Int, description: String) {
        try {
            val fcmTokens: List<String> = if (expense.familyId.isNullOrBlank()) {
                logger.debug("ExpenseUser ${user.id} does not belong to any family, sending notification only to personal devices")
                listOfNotNull(user.fcmToken)
            } else {
                logger.debug("ExpenseUser ${user.id} belongs to family: ${expense.familyId}, sending notification to family members")
                userService.getFamilyMembersFcmTokens(expense.familyId)
                    .flatMap { userService.getAllFcmTokens(it.id) }.distinct()
            }

            logger.debug("Found ${fcmTokens.size} FCM tokens for user: ${user.id}")
            if (fcmTokens.isNotEmpty()) {
                val formattedAmount = "$$amount"
                val invalidTokens = pushNotificationService.sendExpenseNotificationToMultiple(
                    fcmTokens, formattedAmount, description, user.name
                )
                logger.info("FCM notifications sent successfully to ${fcmTokens.size - invalidTokens.size}. Invalid tokens: ${invalidTokens.size}")

                if (invalidTokens.isNotEmpty()) {
                    logger.debug("Cleaning up ${invalidTokens.size} invalid FCM tokens")
                    userDeviceService.removeInvalidTokens(invalidTokens)
                }
            } else {
                logger.debug("No FCM tokens found for user: ${user.id}")
            }
        } catch (e: Exception) {
            logger.error("Failed to send push notification: ${e.message}", e)
        }
    }

    private fun executeWithPagination(
        params: PaginationParams,
        validSortFields: List<String> = VALID_SORT_FIELDS,
        operation: (ValidatedPaginationParams) -> PagedResponse<ExpenseDto>
    ): PagedResponse<ExpenseDto> {
        val validatedParams = validatePaginationParams(
            params.page, params.size, params.lastExpenseId, params.sortBy, params.isAsc, validSortFields
        )
        return operation(validatedParams)
    }

    // API Endpoints
    @GetMapping
    fun getExpenses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?,
        @RequestParam(defaultValue = "date") sortBy: String,
        @RequestParam(defaultValue = "false") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        logger.info("Getting personal expenses for user: ${currentUser.id}, page: $page, size: $size, sortBy: $sortBy, isAsc: $isAsc")

        val params = PaginationParams(page, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(params) { validatedParams ->
            if (validatedParams.lastExpenseId != null) {
                logger.debug("Using cursor-based pagination with lastExpenseId: ${validatedParams.lastExpenseId}")
                expenseService.getPersonalExpensesByUserIdAfterCursor(
                    currentUser.id, validatedParams.lastExpenseId, validatedParams.size,
                    validatedParams.sortBy, validatedParams.isAsc
                )
            } else {
                logger.debug("Using traditional page-based pagination")
                expenseService.getPersonalExpensesByUserIdWithOrder(
                    currentUser.id, validatedParams.page, validatedParams.size,
                    validatedParams.sortBy, validatedParams.isAsc
                )
            }
        }
    }

    @GetMapping("/family")
    fun getExpensesForFamily(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?,
        @RequestParam(defaultValue = "date") sortBy: String,
        @RequestParam(defaultValue = "false") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        logger.info("Getting family expenses for user: ${currentUser.id}, page: $page, size: $size, sortBy: $sortBy, isAsc: $isAsc")

        val familyId = currentUser.familyId
        if (familyId.isNullOrBlank()) {
            logger.warn("ExpenseUser ${currentUser.id} is not part of any family, returning empty family expenses")
            val validatedParams = validatePaginationParams(page, size, lastExpenseId, sortBy, isAsc)
            return PagedResponse(
                content = emptyList(),
                page = validatedParams.page,
                size = validatedParams.size,
                totalElements = 0,
                totalPages = 0,
                isFirst = true,
                isLast = true,
                hasNext = false,
                hasPrevious = false
            )
        }

        logger.debug("ExpenseUser ${currentUser.id} belongs to family: $familyId")
        val params = PaginationParams(page, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(params) { validatedParams ->
            if (validatedParams.lastExpenseId != null) {
                logger.debug("Using cursor-based pagination for family expenses with lastExpenseId: ${validatedParams.lastExpenseId}")
                expenseService.getExpensesByFamilyIdAndUserFamilyAfterCursor(
                    familyId, validatedParams.lastExpenseId, validatedParams.size,
                    validatedParams.sortBy, validatedParams.isAsc
                )
            } else {
                logger.debug("Using traditional page-based pagination for family expenses")
                expenseService.getExpensesByFamilyIdAndUserFamilyWithOrder(
                    familyId, validatedParams.page, validatedParams.size,
                    validatedParams.sortBy, validatedParams.isAsc
                )
            }
        }.also {
            logger.info("Successfully retrieved ${it.content.size} family expenses for user: ${currentUser.id}, family: $familyId")
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(@RequestBody expense: ExpenseDto): ResponseEntity<Any> {
        logger.info("Creating expense request received: amount=${expense.amount}, category=${expense.category}, description=${expense.description}")

        return try {
            val currentUser = getCurrentUserWithValidation()
            logger.debug("Current user ID: ${currentUser.id}")

            // Validate the expense data
            val validationErrors = validateExpenseData(expense)
            if (validationErrors.isNotEmpty()) {
                logger.warn("Expense validation failed with errors: $validationErrors")
                throw ExpenseValidationException("Expense validation failed", validationErrors)
            }

            val currentTime = System.currentTimeMillis()
            val expenseWithUser = expense.copy(
                userId = currentUser.id,
                createdBy = currentUser.id,
                modifiedBy = currentUser.id,
                expenseCreatedOn = currentTime,
                lastModifiedOn = currentTime,
                updatedUserAlias = currentUser.profilePic ?: currentUser.name ?: "Unknown ExpenseUser"
            )

            logger.debug("Attempting to save expense to database")
            val createdExpense = try {
                expenseService.createExpense(expenseWithUser)
            } catch (e: Exception) {
                logger.error("Failed to save expense to database: ${e.message}", e)
                throw ExpenseCreationException("Failed to save expense to database: ${e.message}", e)
            }
            logger.info("Successfully created expense with ID: ${createdExpense.expenseId}")

            // Send FCM notification
            sendExpenseNotification(createdExpense, currentUser, expense.amount, "Expense created: ${expense.description}")

            logger.info("Expense creation completed successfully for user: ${currentUser.id}, expenseId: ${createdExpense.expenseId}")
            ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf(
                    "success" to true,
                    "message" to "Expense created successfully",
                    "expense" to createdExpense
                )
            )

        } catch (e: ExpenseValidationException) {
            logger.error("Expense validation exception: ${e.message}")
            throw e
        } catch (e: ExpenseCreationException) {
            logger.error("Expense creation exception: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error occurred while creating expense: ${e.message}", e)
            throw ExpenseCreationException("An unexpected error occurred while creating the expense: ${e.message}", e)
        }
    }

    @GetMapping("/detail/{id}")
    fun getExpenseById(@PathVariable id: String): ResponseEntity<ExpenseDto> {
        logger.info("Getting expense by ID: $id")
        return try {
            val currentUser = getCurrentUserWithValidation()
            logger.debug("Current user ID: ${currentUser.id}")

            val expense = expenseService.getExpenseById(id)
            val validatedExpense = validateExpenseAccess(expense, currentUser.id, id)

            logger.info("Successfully retrieved expense $id for user ${currentUser.id}")
            ResponseEntity.ok(validatedExpense)
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
        return try {
            val currentUser = getCurrentUserWithValidation()
            logger.debug("Current user ID: ${currentUser.id}")

            val existingExpense = expenseService.getExpenseById(id)
            validateExpenseAccess(existingExpense, currentUser.id, id)

            // Validate the updated expense data
            val validationErrors = validateExpenseData(expense)
            if (validationErrors.isNotEmpty()) {
                throw ExpenseValidationException("Expense validation failed", validationErrors)
            }

            logger.debug("Updating expense $id with new data")
            val updatedExpense = expenseService.updateExpense(
                id, expense.copy(
                    userId = currentUser.id,
                    modifiedBy = currentUser.id,
                    lastModifiedOn = System.currentTimeMillis()
                )
            )

            // Send FCM notification
            sendExpenseNotification(updatedExpense, currentUser, expense.amount, "Expense updated: ${expense.description}")

            logger.info("Successfully updated expense $id for user ${currentUser.id}")
            ResponseEntity.ok(updatedExpense)
        } catch (e: ExpenseValidationException) {
            logger.error("Expense validation1 exception: ${e.message}")
            throw e
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
        return try {
            val currentUser = getCurrentUserWithValidation()
            logger.debug("Current user ID: ${currentUser.id}")

            val existingExpense = expenseService.getExpenseById(id)
                ?: return ResponseEntity.notFound().build()

            if (!canDeleteExpense(existingExpense, currentUser)) {
                logger.warn("Access denied for user ${currentUser.id} trying to delete expense $id (owner: ${existingExpense.userId})")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }

            if (expenseService.deleteExpense(id)) {
                sendExpenseNotification( existingExpense, currentUser, existingExpense.amount, "Expense deleted: ${existingExpense.description}")
                logger.info("Successfully deleted expense $id for user ${currentUser.id} (original owner: ${existingExpense.userId})")
                ResponseEntity.noContent().build()
            } else {
                logger.error("Failed to delete expense $id - deletion failed")
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            logger.error("Error deleting expense $id: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/category/{category}")
    fun getExpensesByCategory(
        @PathVariable category: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        val params = PaginationParams(page, size, null, "date", false)

        return executeWithPagination(params) { validatedParams ->
            expenseService.getExpensesByUserIdAndCategory(
                currentUser.id, category, validatedParams.page, validatedParams.size
            )
        }
    }

    @GetMapping("/between-dates")
    fun getExpensesBetweenDates(
        @RequestParam startDate: String,
        @RequestParam endDate: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        val params = PaginationParams(page, size, null, "date", false)

        return executeWithPagination(params) { validatedParams ->
            val start = LocalDate.parse(startDate).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
            val end = LocalDate.parse(endDate).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
            expenseService.getExpensesByUserIdAndDateRange(
                currentUser.id, start, end, validatedParams.page, validatedParams.size
            )
        }
    }

    @PostMapping("/notify")
    fun notifyExpense(@RequestBody request: ExpenseNotificationRequest): ResponseEntity<String> {
        val currentUser = getCurrentUserWithValidation()

        val expense = expenseService.getExpenseById(request.expenseId)
        validateExpenseAccess(expense, currentUser.id, request.expenseId)

        val fcmTokens = userService.getAllFcmTokens(currentUser.id)
        if (fcmTokens.isEmpty()) {
            return ResponseEntity.badRequest().body("No FCM tokens found. Please update your device token first.")
        }

        val title = "Expense Notification"
        val body = "Expense '${expense!!.description}' of $${expense.amount}"
        val invalidTokens = pushNotificationService.sendNotificationToMultiple(fcmTokens, title, body)

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
        val currentUser = getCurrentUserWithValidation()

        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "Invalid month. Month must be between 1 and 12",
                    "month" to month
                )
            )
        }

        val totalAmount = expenseService.getMonthlyExpenseSum(currentUser.id, year, month)

        return ResponseEntity.ok(
            mapOf(
                "year" to year,
                "month" to month,
                "totalAmount" to totalAmount,
                "userId" to currentUser.id
            )
        )
    }

    @GetMapping("/since")
    fun getExpensesSince(
        @RequestParam lastModified: Long,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?,
        @RequestParam(defaultValue = "lastModifiedOn") sortBy: String,
        @RequestParam(defaultValue = "true") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        val params = PaginationParams(0, size, lastExpenseId, sortBy, isAsc)

        return executeWithPagination(params, VALID_SYNC_SORT_FIELDS) { validatedParams ->
            if (validatedParams.lastExpenseId != null) {
                expenseService.getExpensesSinceWithCursor(
                    currentUser.id, lastModified, validatedParams.lastExpenseId,
                    validatedParams.size, validatedParams.sortBy, validatedParams.isAsc
                )
            } else {
                expenseService.getExpensesSince(
                    currentUser.id, lastModified, validatedParams.size,
                    validatedParams.sortBy, validatedParams.isAsc
                )
            }
        }
    }

    @GetMapping("/since-date")
    fun getExpensesSinceDate(
        @RequestParam date: String,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) lastExpenseId: String?,
        @RequestParam(defaultValue = "date") sortBy: String,
        @RequestParam(defaultValue = "true") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()

        val sinceTimestamp = try {
            LocalDate.parse(date).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
        } catch (e: Exception) {
            throw ExpenseValidationException(
                "Invalid date format. Use YYYY-MM-DD format",
                listOf("Date parsing error: ${e.message}")
            )
        }

        val validSortFields = listOf("date", "lastModifiedOn", "expenseCreatedOn", "amount")
        val params = PaginationParams(0, size, lastExpenseId, sortBy, isAsc)

        return executeWithPagination(params, validSortFields) { validatedParams ->
            if (validatedParams.lastExpenseId != null) {
                expenseService.getExpensesSinceDateWithCursor(
                    currentUser.id, sinceTimestamp, validatedParams.lastExpenseId,
                    validatedParams.size, validatedParams.sortBy, validatedParams.isAsc
                )
            } else {
                expenseService.getExpensesSinceDate(
                    currentUser.id, sinceTimestamp, validatedParams.size,
                    validatedParams.sortBy, validatedParams.isAsc
                )
            }
        }
    }
}