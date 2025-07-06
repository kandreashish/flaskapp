package com.example.expensetracker.controller

import com.example.expensetracker.config.PushNotificationService
import com.example.expensetracker.exception.ExpenseValidationException
import com.example.expensetracker.exception.ExpenseCreationException
import com.example.expensetracker.exception.ExpenseNotFoundException
import com.example.expensetracker.exception.ExpenseAccessDeniedException
import com.example.expensetracker.model.ExpenseDto
import com.example.expensetracker.model.PagedResponse
import com.example.expensetracker.service.ExpenseService
import com.example.expensetracker.service.UserService
import com.example.expensetracker.service.UserDeviceService
import com.example.expensetracker.util.AuthUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
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

    @GetMapping
    fun getAllExpenses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        return expenseService.getExpensesByUserId(currentUserId, page, size)
    }

    @GetMapping("/ordered")
    fun getExpenses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "expenseCreatedOn") sortBy: String,
        @RequestParam(defaultValue = "false") isAsc: Boolean
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()

        // Validate sortBy parameter to prevent SQL injection
        val validSortFields = listOf(
            "expenseCreatedOn", "lastModifiedOn", "amount", "category",
            "description", "date", "userId", "expenseId"
        )

        val safeSortBy = if (validSortFields.contains(sortBy)) sortBy else "expenseCreatedOn"

        return expenseService.getExpensesByUserIdWithOrder(currentUserId, page, size, safeSortBy, isAsc)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(@RequestBody expense: ExpenseDto): ResponseEntity<Any> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()

            // Validate the expense data
            val validationErrors = validateExpense(expense)
            if (validationErrors.isNotEmpty()) {
                throw ExpenseValidationException(
                    "Expense validation failed",
                    validationErrors
                )
            }

            val expenseWithUser = expense.copy(
                userId = currentUserId,
                createdBy = currentUserId,
                modifiedBy = currentUserId,
                expenseCreatedOn = System.currentTimeMillis(),
                lastModifiedOn = System.currentTimeMillis()
            )

            val createdExpense = try {
                expenseService.createExpense(expenseWithUser)
            } catch (e: Exception) {
                throw ExpenseCreationException(
                    "Failed to save expense to database: ${e.message}",
                    e
                )
            }

            // Send FCM notification to all user devices after creating expense
            try {
                val user = userService.findById(currentUserId)
                if (user != null) {
                    val fcmTokens = userService.getAllFcmTokens(currentUserId)
                    if (fcmTokens.isNotEmpty()) {
                        val formattedAmount = "$${expense.amount}"
                        val invalidTokens = pushNotificationService.sendExpenseNotificationToMultiple(
                            fcmTokens, formattedAmount, expense.description, user.name
                        )
                        // Clean up invalid tokens
                        if (invalidTokens.isNotEmpty()) {
                            userDeviceService.removeInvalidTokens(invalidTokens)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log notification error but don't fail the expense creation
                println("Warning: Failed to send push notification for expense ${createdExpense.expenseId}: ${e.message}")
            }

            ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf(
                    "success" to true,
                    "message" to "Expense created successfully",
                    "expense" to createdExpense
                )
            )

        } catch (e: ExpenseValidationException) {
            // Re-throw validation exceptions to be handled by GlobalExceptionHandler
            throw e
        } catch (e: ExpenseCreationException) {
            // Re-throw creation exceptions to be handled by GlobalExceptionHandler
            throw e
        } catch (e: Exception) {
            // Catch any unexpected exceptions
            throw ExpenseCreationException(
                "An unexpected error occurred while creating the expense: ${e.message}",
                e
            )
        }
    }

    private fun validateExpense(expense: ExpenseDto): List<String> {
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
        if (expense.familyId.isNotBlank() && expense.familyId.length < 3) {
            errors.add("Family ID format is invalid")
        }

        return errors
    }

    @GetMapping("/{id}")
    fun getExpenseById(@PathVariable id: String): ResponseEntity<ExpenseDto> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            val expense = expenseService.getExpenseById(id)
                ?: throw ExpenseNotFoundException("Expense with ID '$id' not found")

            if (expense.userId != currentUserId) {
                throw ExpenseAccessDeniedException("You don't have permission to view this expense")
            }

            ResponseEntity.ok(expense)
        } catch (e: ExpenseNotFoundException) {
            throw e
        } catch (e: ExpenseAccessDeniedException) {
            throw e
        } catch (e: Exception) {
            throw ExpenseCreationException("Error retrieving expense: ${e.message}", e)
        }
    }

    @PutMapping("/{id}")
    fun updateExpense(@PathVariable id: String, @RequestBody expense: ExpenseDto): ResponseEntity<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        return try {
            val existingExpense = expenseService.getExpenseById(id)
            if (existingExpense?.userId != currentUserId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }

            val updatedExpense = expenseService.updateExpense(
                id, expense.copy(
                    userId = currentUserId,
                    modifiedBy = currentUserId
                )
            )
            ResponseEntity.ok(updatedExpense)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteExpense(@PathVariable id: String): ResponseEntity<Void> {
        val currentUserId = authUtil.getCurrentUserId()
        val existingExpense = expenseService.getExpenseById(id)

        if (existingExpense?.userId != currentUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return if (expenseService.deleteExpense(id)) {
            ResponseEntity.noContent().build()
        } else {
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
        return expenseService.getExpensesByUserIdAndCategory(currentUserId, category, page, size)
    }

    @GetMapping("/between-dates")
    fun getExpensesBetweenDates(
        @RequestParam startDate: String,
        @RequestParam endDate: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        val start = LocalDate.parse(startDate).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
        val end = LocalDate.parse(endDate).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
        return expenseService.getExpensesByUserIdAndDateRange(currentUserId, start, end, page, size)
    }

    @GetMapping("/family")
    fun getFamilyExpenses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        // This would need to get the user's family ID first
        // For now, we'll return the user's expenses
        return expenseService.getExpensesByUserId(currentUserId, page, size)
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
}
