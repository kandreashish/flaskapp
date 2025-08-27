package com.lavish.expensetracker.controller

import com.lavish.expensetracker.exception.*
import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.service.ExpenseNotificationService
import com.lavish.expensetracker.service.ExpenseService
import com.lavish.expensetracker.service.UserDeviceService
import com.lavish.expensetracker.service.UserService
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Extracted application-layer logic for ExpenseController endpoints.
 * Controller delegates directly to this class to keep endpoint mappings thin.
 */
class ExpenseControllerHandler(
    private val expenseService: ExpenseService,
    private val authUtil: AuthUtil,
    private val userService: UserService,
    private val userDeviceService: UserDeviceService,
    private val familyRepository: FamilyRepository,
    private val notificationRepository: NotificationRepository,
    private val expenseNotificationService: ExpenseNotificationService
) {

    // Internal pagination containers
    private data class PaginationParams(
        val page: Int,
        val size: Int,
        val lastExpenseId: String?,
        val sortBy: String,
        val isAsc: Boolean
    )
    private data class ValidatedPaginationParams(
        val page: Int,
        val size: Int,
        val lastExpenseId: String?,
        val sortBy: String,
        val isAsc: Boolean
    )

    // ===== Public facade methods (mirroring endpoints) =====
    fun getExpenses(page: Int, size: Int, lastExpenseId: String?, sortBy: String, isAsc: Boolean): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        val params = PaginationParams(page, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(params) { v ->
            if (v.lastExpenseId != null) expenseService.getPersonalExpensesByUserIdAfterCursor(
                currentUser.id, v.lastExpenseId, v.size, v.sortBy, v.isAsc
            ) else expenseService.getPersonalExpensesByUserIdWithOrder(
                currentUser.id, v.page, v.size, v.sortBy, v.isAsc
            )
        }
    }

    fun getFamilyExpenses(page: Int, size: Int, lastExpenseId: String?, sortBy: String, isAsc: Boolean): PagedResponse<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        val familyId = currentUser.familyId
        if (!familyId.isNullOrBlank()) {
            val family = familyRepository.findById(familyId).orElse(null)
                ?: throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "The specified family does not exist")
            if (!family.membersIds.contains(currentUser.id) && family.headId != currentUser.id)
                throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Cannot access expenses for a family you are not a member of")
        }
        if (familyId.isNullOrBlank()) throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "you are not a member of any family, cannot fetch family expenses")
        val p = PaginationParams(page, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(p) { v ->
            if (v.lastExpenseId != null) expenseService.getExpensesByFamilyIdAndUserFamilyAfterCursor(
                familyId, v.lastExpenseId, v.size, v.sortBy, v.isAsc
            ) else expenseService.getExpensesByFamilyIdAndUserFamilyWithOrder(
                familyId, v.page, v.size, v.sortBy, v.isAsc
            )
        }
    }

    fun createExpense(expense: ExpenseDto): ResponseEntity<Any> {
        val currentUser = getCurrentUserWithValidation()
        val validationErrors = validateExpenseData(expense, currentUser)
        if (validationErrors.isNotEmpty()) throw ExpenseValidationException("Expense validation failed", validationErrors)
        if (!expense.familyId.isNullOrBlank()) {
            val family = familyRepository.findById(expense.familyId).orElse(null)
                ?: return preconditionFailed("Family not found", "The specified family does not exist")
            if (!family.membersIds.contains(currentUser.id) && family.headId != currentUser.id)
                return preconditionFailed("You are not part of this family", "Cannot add expense to a family you are not a member of")
        }
        val now = System.currentTimeMillis()
        val expenseWithUser = expense.copy(
            userId = currentUser.id,
            createdBy = currentUser.id,
            modifiedBy = currentUser.id,
            expenseCreatedOn = now,
            lastModifiedOn = now,
            currencyPrefix = currentUser.currencyPreference,
            updatedUserName = currentUser.name ?: currentUser.email
        )
        val created = try { expenseService.createExpense(expenseWithUser) } catch (e: Exception) {
            throw ExpenseCreationException("Failed to save expense to database: ${e.message}", e)
        }
        sendExpenseNotification(
            type = if (expense.familyId.isNullOrBlank()) NotificationType.EXPENSE_ADDED else NotificationType.FAMILY_EXPENSE_ADDED,
            title = "New Expense Added",
            body = "${currentUser.name} added expense: ${expense.description} - ${currentUser.currencyPreference + expense.amount}",
            expense = created,
            user = currentUser,
            amount = expense.amount,
            description = expense.description
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to "Expense created successfully", "expense" to created))
    }

    fun getExpenseById(id: String): ResponseEntity<ExpenseDto> {
        val currentUser = getCurrentUserWithValidation()
        val expense = expenseService.getExpenseById(id)
        val validated = validateExpenseAccess(expense, currentUser, id)
        return ResponseEntity.ok(validated)
    }

    fun updateExpense(id: String, expense: ExpenseDto): ResponseEntity<Any> {
        return try {
            val currentUser = getCurrentUserWithValidation()
            val existing = expenseService.getExpenseById(id)
                ?: return preconditionFailed("Expense not found", "The specified expense does not exist")
            validateExpenseAccess(existing, currentUser, id)
            val validationErrors = validateExpenseData(expense, currentUser)
            if (validationErrors.isNotEmpty()) throw ExpenseValidationException("Expense validation failed", validationErrors)
            if (!expense.familyId.isNullOrBlank()) {
                val family = familyRepository.findById(expense.familyId).orElse(null)
                    ?: return preconditionFailed("Family not found", "The specified family does not exist")
                if (!family.membersIds.contains(currentUser.id) && family.headId != currentUser.id)
                    return preconditionFailed("You are not part of this family", "Cannot update expense to a family you are not a member of")
            }
            val updated = expenseService.updateExpense(id, expense.copy(
                userId = currentUser.id,
                modifiedBy = currentUser.id,
                lastModifiedOn = System.currentTimeMillis()
            ))
            sendExpenseNotification(
                type = if (existing.familyId.isNullOrBlank()) NotificationType.EXPENSE_UPDATED else NotificationType.FAMILY_EXPENSE_UPDATED,
                title = "Expense Updated",
                body = "${currentUser.name} updated expense: ${expense.description} - ${currentUser.currencyPreference + expense.amount}",
                expense = updated,
                user = currentUser,
                amount = expense.amount,
                description = expense.description
            )
            ResponseEntity.ok(updated)
        } catch (e: ExpenseValidationException) {
            throw e
        } catch (e: ExpenseAccessDeniedException) {
            throw e
        } catch (e: ExpenseNotFoundException) {
            throw e
        } catch (_: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    fun deleteExpense(id: String): ResponseEntity<Any> {
        return try {
            val currentUser = getCurrentUserWithValidation()
            val existing = expenseService.getExpenseById(id) ?: return ResponseEntity.notFound().build()
            if (!canDeleteExpense(existing, currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            if (expenseService.deleteExpense(id)) {
                sendExpenseNotification(
                    type = if (existing.familyId.isNullOrBlank()) NotificationType.EXPENSE_DELETED else NotificationType.FAMILY_EXPENSE_DELETED,
                    title = "Expense Deleted",
                    body = "${currentUser.name} deleted expense: ${existing.description} - ${currentUser.currencyPreference + existing.amount}",
                    expense = existing,
                    user = currentUser,
                    amount = existing.amount,
                    description = existing.description
                )
                ResponseEntity.ok(mapOf("message" to "Expense Deleted Successfully"))
            } else ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    fun getExpensesByCategory(category: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val user = getCurrentUserWithValidation()
        val params = PaginationParams(page, size, null, "date", false)
        return executeWithPagination(params) { v ->
            expenseService.getExpensesByUserIdAndCategory(user.id, category, v.page, v.size)
        }
    }

    fun getExpensesBetweenDates(startDate: String, endDate: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val user = getCurrentUserWithValidation()
        val params = PaginationParams(page, size, null, "date", false)
        return executeWithPagination(params) { v ->
            val start = LocalDate.parse(startDate).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
            val end = LocalDate.parse(endDate).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
            expenseService.getExpensesByUserIdAndDateRange(user.id, start, end, v.page, v.size)
        }
    }

    fun notifyExpense(request: ExpenseController.ExpenseNotificationRequest): ResponseEntity<String> {
        val currentUser = getCurrentUserWithValidation()
        val expense = expenseService.getExpenseById(request.expenseId)
        validateExpenseAccess(expense, currentUser, request.expenseId)
        val tokens = userService.getAllFcmTokens(currentUser.id)
        if (tokens.isEmpty()) return ResponseEntity.badRequest().body("No FCM tokens found. Please update your device token first.")
        val title = "Expense Notification"
        val body = "Expense '${expense!!.description}' of ${currentUser.currencyPreference + expense.amount}"
        val invalid = expenseNotificationService.sendNotificationToMultiple(tokens, title, body, NotificationType.GENERAL)
        if (invalid.isNotEmpty()) userDeviceService.removeInvalidTokens(invalid)
        return ResponseEntity.ok("Notification sent successfully to ${tokens.size - invalid.size} device(s)")
    }

    fun getMonthlyExpenseSum(year: Int, month: Int): ResponseEntity<Map<String, Any>> {
        val user = getCurrentUserWithValidation()
        if (month !in 1..12 || year < 2000 || year > LocalDate.now().year) return badRequest("Invalid month. Month must be between 1 and 12", "month" to month)
        val total = expenseService.getMonthlyExpenseSum(user.id, year, month)
        return ResponseEntity.ok(mapOf(
            "year" to year,
            "month" to month,
            "totalAmount" to total,
            "userId" to user.id,
            "expenseCount" to expenseService.getExpenseCountByUserIdAndMonth(user.id, year, month)
        ))
    }

    fun getFamilyMonthlyExpenseSum(year: Int, month: Int): ResponseEntity<Map<String, Any>> {
        val user = getCurrentUserWithValidation(); val familyId = user.familyId
        if (familyId.isNullOrBlank()) return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(mapOf("error" to "You are not part of any family", "userId" to user.id))
        if (year < 2000 || year > LocalDate.now().year) return badRequest("Invalid year. Year must be between 2000 and current year", "year" to year)
        if (month !in 1..12) return badRequest("Invalid month. Month must be between 1 and 12", "month" to month)
        val total = expenseService.getFamilyMonthlyExpenseSum(year, month, familyId)
        return ResponseEntity.ok(mapOf(
            "year" to year,
            "month" to month,
            "totalAmount" to total,
            "expenseCount" to expenseService.getFamilyExpenseCountByUserIdAndMonth(familyId, year, month)
        ))
    }

    fun getExpensesSince(lastModified: Long, size: Int, lastExpenseId: String?, sortBy: String, isAsc: Boolean): PagedResponse<ExpenseDto> {
        val user = getCurrentUserWithValidation()
        val p = PaginationParams(0, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(p, VALID_SYNC_SORT_FIELDS) { v ->
            if (v.lastExpenseId != null) expenseService.getExpensesSinceWithCursor(
                user.id, lastModified, v.lastExpenseId, v.size, v.sortBy, v.isAsc
            ) else expenseService.getExpensesSince(
                user.id, lastModified, v.size, v.sortBy, v.isAsc
            )
        }
    }

    fun getExpensesSinceDate(date: String, size: Int, lastExpenseId: String?, sortBy: String, isAsc: Boolean): PagedResponse<ExpenseDto> {
        val user = getCurrentUserWithValidation()
        val sinceTs = try { LocalDate.parse(date).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000 } catch (e: Exception) {
            throw ExpenseValidationException("Invalid date format. Use YYYY-MM-DD format", listOf("Date parsing error: ${e.message}"))
        }
        val allowed = listOf("date", "lastModifiedOn", "expenseCreatedOn", "amount")
        val p = PaginationParams(0, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(p, allowed) { v ->
            if (v.lastExpenseId != null) expenseService.getExpensesSinceDateWithCursor(
                user.id, sinceTs, v.lastExpenseId, v.size, v.sortBy, v.isAsc
            ) else expenseService.getExpensesSinceDate(
                user.id, sinceTs, v.size, v.sortBy, v.isAsc
            )
        }
    }

    fun getFamilyExpensesSince(lastModified: Long, size: Int, lastExpenseId: String?, sortBy: String, isAsc: Boolean): PagedResponse<ExpenseDto> {
        val user = getCurrentUserWithValidation(); val familyId = user.familyId
        if (!familyId.isNullOrBlank()) {
            val family = familyRepository.findById(familyId).orElse(null) ?: throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "The specified family does not exist")
            if (!family.membersIds.contains(user.id) && family.headId != user.id) throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Cannot access expenses for a family you are not a member of")
        }
        if (familyId.isNullOrBlank()) throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "you are not a member of any family, cannot fetch family expenses")
        val p = PaginationParams(0, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(p, VALID_SYNC_SORT_FIELDS) { v ->
            if (v.lastExpenseId != null) expenseService.getFamilyExpensesSinceWithCursor(
                familyId, lastModified, v.lastExpenseId, v.size, v.sortBy, v.isAsc
            ) else expenseService.getFamilyExpensesSince(
                familyId, lastModified, v.size, v.sortBy, v.isAsc
            )
        }
    }

    fun getFamilyExpensesSinceDate(date: String, size: Int, lastExpenseId: String?, sortBy: String, isAsc: Boolean): PagedResponse<ExpenseDto> {
        val user = getCurrentUserWithValidation(); val familyId = user.familyId
        if (!familyId.isNullOrBlank()) {
            val family = familyRepository.findById(familyId).orElse(null) ?: throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "The specified family does not exist")
            if (!family.membersIds.contains(user.id) && family.headId != user.id) throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Cannot access expenses for a family you are not a member of")
        }
        if (familyId.isNullOrBlank()) throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "you are not a member of any family, cannot fetch family expenses")
        val sinceTs = try { LocalDate.parse(date).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000 } catch (e: Exception) {
            throw ExpenseValidationException("Invalid date format. Use YYYY-MM-DD format", listOf("Date parsing error: ${e.message}"))
        }
        val allowed = listOf("date", "lastModifiedOn", "expenseCreatedOn", "amount")
        val p = PaginationParams(0, size, lastExpenseId, sortBy, isAsc)
        return executeWithPagination(p, allowed) { v ->
            if (v.lastExpenseId != null) expenseService.getFamilyExpensesSinceWithCursor(
                familyId, sinceTs, v.lastExpenseId, v.size, v.sortBy, v.isAsc
            ) else expenseService.getFamilyExpensesSince(
                familyId, sinceTs, v.size, v.sortBy, v.isAsc
            )
        }
    }

    // ===== Helpers (copied from controller) =====
    private fun getCurrentUserWithValidation(): ExpenseUser {
        val currentUserId = try { authUtil.getCurrentUserId() } catch (e: ResponseStatusException) {
            throw when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required. Please provide a valid JWT token.")
                HttpStatus.FORBIDDEN -> ResponseStatusException(HttpStatus.BAD_REQUEST, "Please re-authenticate.")
                else -> ResponseStatusException(HttpStatus.BAD_REQUEST, "Authentication failed: ${e.reason}")
            }
        }
        return userService.findById(currentUserId) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ExpenseUser not found")
    }

    private fun validateExpenseData(expense: ExpenseDto, currentUser: ExpenseUser): List<String> {
        val errors = mutableListOf<String>()
        val maxAmount = ExpenseController.MAX_AMOUNT
        when {
            expense.amount <= 0 -> errors.add("Amount is required and must be greater than 0")
            expense.amount > maxAmount -> errors.add("Amount cannot exceed ${currentUser.currencyPreference + maxAmount.toInt()}")
            expense.amount.toString().length > 10 -> errors.add("Amount value is too large")
        }
        val validCategories = listOf(
            "FOOD", "ENTERTAINMENT", "FUN", "BILLS", "TRAVEL",
            "UTILITIES", "HEALTH", "SHOPPING", "EDUCATION", "OTHERS"
        )
        if (expense.category.isBlank()) errors.add("Category is required")
        else if (!validCategories.contains(expense.category.uppercase())) errors.add("Category must be one of: ${validCategories.joinToString(", ")}")
        if (expense.description.length > 500) errors.add("Description cannot exceed 500 characters")
        val suspiciousPatterns = listOf("<script", "javascript:", "onerror=", "onload=")
        if (suspiciousPatterns.any { expense.description.lowercase().contains(it) }) errors.add("Description contains invalid characters")
        val now = System.currentTimeMillis()
        when {
            expense.date <= 0 -> errors.add("Date is required and must be a valid timestamp")
            expense.date > now + 86_400_000L -> errors.add("Date cannot be more than 1 day in the future")
            expense.date < now - 10L * 365 * 24 * 60 * 60 * 1000 -> errors.add("Date cannot be more than 10 years in the past")
        }
        if (expense.userId.isNotBlank() && expense.userId.length < 3) errors.add("ExpenseUser ID format is invalid")
        if (expense.familyId?.isNotBlank() == true && expense.familyId.length < 3) errors.add("Family ID format is invalid")
        return errors
    }

    private fun validateExpenseAccess(expense: ExpenseDto?, currentUser: ExpenseUser, expenseId: String): ExpenseDto {
        if (expense == null) throw ExpenseNotFoundException("Expense with ID '$expenseId' not found")
        if (expense.userId == currentUser.id) return expense
        if (currentUser.familyId != null && currentUser.familyId.isNotBlank()) {
            val owner = userService.findById(expense.userId)
            if (owner != null && owner.familyId == currentUser.familyId && owner.familyId.isNotBlank()) return expense
        }
        throw ExpenseAccessDeniedException("You don't have permission to view this expense")
    }

    /**
     * Determines if the current user has permission to delete the given expense.
     * A user can delete an expense if:
     * 1. They are the owner of the expense, OR
     * 2. The expense belongs to a family and the user is a member of that family.
     *
     * @param expense The expense to check deletion permissions for
     * @param currentUser The user attempting to delete the expense
     * @return true if the user can delete the expense, false otherwise
     */
    private fun canDeleteExpense(expense: ExpenseDto, currentUser: ExpenseUser): Boolean {
        return expense.userId == currentUser.id || (currentUser.familyId != null && currentUser.familyId == expense.familyId)
    }

    private fun sendExpenseNotification(
        type: NotificationType,
        title: String,
        body: String,
        expense: ExpenseDto,
        user: ExpenseUser,
        amount: Double,
        description: String
    ) {
        try {
            val tokens = if (expense.familyId.isNullOrBlank()) userService.getAllFcmTokens(user.id) else getFamilyTokens(expense.familyId)
            if (tokens.isEmpty()) return
            val invalid = expenseNotificationService.sendExpenseNotificationToMultiple(
                title = title,
                body = body,
                type = type,
                tokens = tokens,
                amount = "${user.currencyPreference}$amount",
                description = description,
                userId = user.id,
                expenseId = expense.expenseId
            )
            if (invalid.isNotEmpty()) userDeviceService.removeInvalidTokens(invalid)
            saveNotificationIfFamily(type, title, body, expense, user)
        } catch (_: Exception) {
            // Swallow - non critical
        }
    }

    private fun getFamilyTokens(familyId: String): List<String> = try {
        userService.getFamilyMembersFcmTokens(familyId).flatMap { m ->
            try { userService.getAllFcmTokens(m.id) } catch (_: Exception) { emptyList() }
        }.distinct().filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    private fun saveNotificationIfFamily(type: NotificationType, title: String, body: String, expense: ExpenseDto, user: ExpenseUser) {
        if (expense.familyId.isNullOrBlank()) return
        val family = familyRepository.findById(expense.familyId).orElse(null) ?: return
        val members = userService.getFamilyMembersFcmTokens(expense.familyId)
        members.forEach { m ->
            try {
                notificationRepository.save(
                    Notification(
                        title = title.take(255),
                        message = body.take(1000),
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        familyId = expense.familyId.take(50),
                        familyAlias = family.aliasName.take(10),
                        senderName = (user.name ?: "Unknown User").take(100),
                        senderId = user.id.take(50),
                        receiverId = m.id.take(50),
                        actionable = false,
                        type = type
                    )
                )
            } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun executeWithPagination(
        params: PaginationParams,
        validSortFields: List<String> = VALID_SORT_FIELDS,
        op: (ValidatedPaginationParams) -> PagedResponse<ExpenseDto>
    ): PagedResponse<ExpenseDto> {
        val page = maxOf(0, params.page)
        val size = when {
            params.size <= 0 -> 10
            params.size > 500 -> 500
            else -> params.size
        }
        val sort = if (validSortFields.contains(params.sortBy)) params.sortBy else "date"
        return op(ValidatedPaginationParams(page, size, params.lastExpenseId, sort, params.isAsc))
    }

    private fun preconditionFailed(message: String, error: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
            mapOf(
                "success" to false,
                "message" to message,
                "error" to error
            )
        )

    private fun badRequest(msg: String, vararg extra: Pair<String, Any?>): ResponseEntity<Map<String, Any>> =
        ResponseEntity.badRequest().body(buildMap {
            put("error", msg)
            extra.forEach { (k, v) -> if (v != null) put(k, v) }
        })

    companion object {
        private val VALID_SORT_FIELDS = listOf(
            "expenseCreatedOn", "lastModifiedOn", "amount", "category",
            "description", "date", "userId", "expenseId"
        )
        private val VALID_SYNC_SORT_FIELDS = listOf("lastModifiedOn", "expenseCreatedOn", "date")
    }
}
