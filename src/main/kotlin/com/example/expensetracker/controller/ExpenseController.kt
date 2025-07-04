package com.example.expensetracker.controller

import com.example.expensetracker.config.PushNotificationService
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

    @GetMapping("/{id}")
    fun getExpenseById(@PathVariable id: String): ResponseEntity<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        return expenseService.getExpenseById(id)?.let { expense ->
            if (expense.userId == currentUserId) {
                ResponseEntity.ok(expense)
            } else {
                ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
        } ?: ResponseEntity.notFound().build()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(@RequestBody expense: ExpenseDto): ExpenseDto {
        val currentUserId = authUtil.getCurrentUserId()
        val expenseWithUser = expense.copy(
            userId = currentUserId,
            createdBy = currentUserId,
            modifiedBy = currentUserId,
            expenseCreatedOn = System.currentTimeMillis(),
            lastModifiedOn = System.currentTimeMillis()
        )
        val createdExpense = expenseService.createExpense(expenseWithUser)

        // Send FCM notification to all user devices after creating expense
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

        return createdExpense
    }

    @PutMapping("/{id}")
    fun updateExpense(@PathVariable id: String, @RequestBody expense: ExpenseDto): ResponseEntity<ExpenseDto> {
        val currentUserId = authUtil.getCurrentUserId()
        return try {
            val existingExpense = expenseService.getExpenseById(id)
            if (existingExpense?.userId != currentUserId) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }

            val updatedExpense = expenseService.updateExpense(id, expense.copy(
                userId = currentUserId,
                modifiedBy = currentUserId
            ))
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
}
