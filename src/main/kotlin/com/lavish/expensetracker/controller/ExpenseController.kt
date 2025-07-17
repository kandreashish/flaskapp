package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.exception.ExpenseAccessDeniedException
import com.lavish.expensetracker.exception.ExpenseCreationException
import com.lavish.expensetracker.exception.ExpenseNotFoundException
import com.lavish.expensetracker.exception.ExpenseValidationException
import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.model.NotificationType
import com.lavish.expensetracker.model.PagedResponse
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
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
    private val userDeviceService: UserDeviceService,
    private val familyRepository: FamilyRepository,
    private val notificationRepository: NotificationRepository // Injecting NotificationRepository
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
                    HttpStatus.BAD_REQUEST,
                    "Please re-authenticate."
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

    private fun validateExpenseAccess(expense: ExpenseDto?, currentUser: ExpenseUser, expenseId: String): ExpenseDto {
        if (expense == null) {
            throw ExpenseNotFoundException("Expense with ID '$expenseId' not found")
        }

        // Allow access if user is the expense owner
        if (expense.userId == currentUser.id) {
            return expense
        }

        // Allow access if both users are in the same family
        if (currentUser.familyId != null && currentUser.familyId.isNotBlank()) {
            val expenseOwner = userService.findById(expense.userId)
            if (expenseOwner != null &&
                expenseOwner.familyId == currentUser.familyId &&
                expenseOwner.familyId.isNotBlank()
            ) {
                logger.debug("Family member ${currentUser.id} accessing expense $expenseId owned by ${expense.userId} in family ${currentUser.familyId}")
                return expense
            }
        }

        logger.warn("Access denied for user ${currentUser.id} trying to access expense $expenseId owned by ${expense.userId}")
        throw ExpenseAccessDeniedException("You don't have permission to view this expense")
    }

    private fun canDeleteExpense(expense: ExpenseDto, currentUser: ExpenseUser): Boolean {
        val expenseOwner = userService.findById(expense.userId)
            ?: return false

        return expense.userId == currentUser.id ||
                (currentUser.familyId != null &&
                        currentUser.familyId == expenseOwner.familyId &&
                        currentUser.familyId.isNotBlank())
    }

    private fun sendExpenseNotification(
        type: NotificationType,
        title: String,
        body: String,
        expense: ExpenseDto,
        user: ExpenseUser,
        amount: Int,
        description: String
    ) {
        try {
            val fcmTokens = getFcmTokensForNotification(expense, user)

            if (fcmTokens.isEmpty()) {
                logger.debug("No FCM tokens found for notification - user: ${user.id}, family: ${expense.familyId}")
                return
            }

            sendNotificationToTokens(
                type = type,
                title = title,
                body = body,
                fcmTokens = fcmTokens,
                amount = amount,
                description = description
            )

            // Save notification to database
            saveNotificationToDatabase(
                type = type,
                title = title,
                body = body,
                expense = expense,
                user = user
            )

        } catch (e: Exception) {
            logger.error(
                "Failed to send expense notification for user ${user.id}, expense ${expense.expenseId}: ${e.message}",
                e
            )
        }
    }

    private fun saveNotificationToDatabase(
        type: NotificationType,
        title: String,
        body: String,
        expense: ExpenseDto,
        user: ExpenseUser
    ) {
        try {
            // Only save notifications for family expenses
            if (expense.familyId.isNullOrBlank()) {
                logger.debug("Skipping database notification save for personal expense: ${expense.expenseId}")
                return
            }

            // Get family information
            val family = familyRepository.findById(expense.familyId).orElse(null)
            if (family == null) {
                logger.warn("Cannot save notification - family not found: ${expense.familyId}")
                return
            }

            // Save notification for each family member
            val familyMembers = userService.getFamilyMembersFcmTokens(expense.familyId)

            familyMembers.forEach { familyMember ->
                try {
                    val notification = Notification(
                        title = title.take(255),
                        message = body.take(1000),
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        familyId = expense.familyId.take(50),
                        familyAlias = family.aliasName.take(10),
                        senderName = (user.name ?: "Unknown User").take(100),
                        senderId = user.id.take(50),
                        receiverId = familyMember.id.take(50),
                        actionable = false,
                        type = type
                    )

                    notificationRepository.save(notification)
                    logger.debug("Notification saved to database for expense ${expense.expenseId}, user ${familyMember.id}")
                } catch (e: Exception) {
                    logger.error("Failed to save notification for family member ${familyMember.id}: ${e.message}", e)
                }
            }

            logger.info("Family expense notifications saved to database for expense ${expense.expenseId} in family ${expense.familyId}")

        } catch (e: Exception) {
            logger.error("Failed to save family expense notifications to database: ${e.message}", e)
        }
    }

    private fun getFcmTokensForNotification(expense: ExpenseDto, user: ExpenseUser): List<String> {
        return try {
            if (expense.familyId.isNullOrBlank()) {
                // Personal expense - notify only the user's devices
                logger.debug("Personal expense notification for user: ${user.id}")
                getUserFcmTokens(user.id)
            } else {
                // Family expense - notify all family members
                logger.debug("Family expense notification for family: ${expense.familyId}")
                getFamilyFcmTokens(expense.familyId)
            }
        } catch (e: Exception) {
            logger.error("Error retrieving FCM tokens for user ${user.id}, family ${expense.familyId}: ${e.message}", e)
            emptyList()
        }
    }

    private fun getUserFcmTokens(userId: String): List<String> {
        return try {
            userService.getAllFcmTokens(userId).filter { it.isNotBlank() }
        } catch (e: Exception) {
            logger.error("Error getting FCM tokens for user $userId: ${e.message}", e)
            emptyList()
        }
    }

    private fun getFamilyFcmTokens(familyId: String): List<String> {
        return try {
            userService.getFamilyMembersFcmTokens(familyId)
                .map { familyMember ->
                    try {
                        userService.getAllFcmTokens(familyMember.id)
                    } catch (e: Exception) {
                        logger.warn("Failed to get tokens for family member ${familyMember.id}: ${e.message}")
                        emptyList()
                    }
                }
                .flatten()
                .distinct()
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            logger.error("Error getting family FCM tokens for family $familyId: ${e.message}", e)
            emptyList()
        }
    }

    private fun sendNotificationToTokens(
        type: NotificationType,
        title: String,
        body: String,
        fcmTokens: List<String>,
        amount: Int,
        description: String
    ) {
        try {
            logger.debug("Sending notification to ${fcmTokens.size} FCM tokens")

            val formattedAmount = formatAmount(amount)
            val invalidTokens = pushNotificationService.sendExpenseNotificationToMultiple(
                title, body, type,
                fcmTokens, formattedAmount, description
            )

            val successfulNotifications = fcmTokens.size - invalidTokens.size
            logger.info("Expense notification sent to $successfulNotifications devices. Invalid tokens: ${invalidTokens.size}")

            if (invalidTokens.isNotEmpty()) {
                cleanupInvalidTokens(invalidTokens)
            }

        } catch (e: Exception) {
            logger.error("Error sending notifications to FCM tokens: ${e.message}", e)
            throw e
        }
    }

    private fun formatAmount(amount: Int): String {
        return try {
            "$$amount"
        } catch (e: Exception) {
            logger.warn("Error formatting amount $amount: ${e.message}")
            "$0"
        }
    }

    private fun cleanupInvalidTokens(invalidTokens: List<String>) {
        try {
            logger.debug("Cleaning up ${invalidTokens.size} invalid FCM tokens")
            userDeviceService.removeInvalidTokens(invalidTokens)
            logger.debug("Successfully removed invalid FCM tokens")
        } catch (e: Exception) {
            logger.error("Failed to cleanup invalid FCM tokens: ${e.message}", e)
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

        if (!familyId.isNullOrBlank()) {
            val family = familyRepository.findById(familyId).orElse(null)
            if (family == null) {
                logger.warn("User ${currentUser.id} attempted to access expenses for non-existent family: ${familyId}")
                throw ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "The specified family does not exist"
                )
            }

            if (!family.membersIds.contains(currentUser.id) && family.headId != currentUser.id) {
                logger.warn("User ${currentUser.id} attempted to access expenses for family ${familyId} but is not a member")
                throw ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "Cannot access expenses for a family you are not a member of"
                )
            }

            logger.debug("Family membership validated for user ${currentUser.id} in family ${familyId}")
        }

        if (familyId.isNullOrBlank()) {
            logger.warn("ExpenseUser ${currentUser.id} is not part of any family, returning empty family expenses")
            throw ResponseStatusException(
                HttpStatus.PRECONDITION_FAILED,
                "you are not a member of any family, cannot fetch family expenses"
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

            // Validate family membership if familyId is provided
            if (!expense.familyId.isNullOrBlank()) {
                val family = familyRepository.findById(expense.familyId).orElse(null)
                if (family == null) {
                    logger.warn("User ${currentUser.id} attempted to add expense to non-existent family: ${expense.familyId}")
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                        mapOf(
                            "success" to false,
                            "message" to "Family not found",
                            "error" to "The specified family does not exist"
                        )
                    )
                }

                if (!family.membersIds.contains(currentUser.id) && family.headId != currentUser.id) {
                    logger.warn("User ${currentUser.id} attempted to add expense to family ${expense.familyId} but is not a member")
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                        mapOf(
                            "success" to false,
                            "message" to "You are not part of this family",
                            "error" to "Cannot add expense to a family you are not a member of"
                        )
                    )
                }

                logger.debug("Family membership validated for user ${currentUser.id} in family ${expense.familyId}")
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
            sendExpenseNotification(
                type = if (expense.familyId == null || expense.familyId.isBlank()) NotificationType.EXPENSE_ADDED else NotificationType.FAMILY_EXPENSE_ADDED,
                title = "New Expense Added",
                body = "${currentUser.name} added expense: ${expense.description} - $${expense.amount}",
                expense = createdExpense,
                user = currentUser,
                amount = expense.amount,
                description = expense.description
            )

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
            logger.debug("Current user2 ID: ${currentUser.id}")

            val expense = expenseService.getExpenseById(id)
            val validatedExpense = validateExpenseAccess(expense, currentUser, id)

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
    fun updateExpense(@PathVariable id: String, @RequestBody expense: ExpenseDto): ResponseEntity<Any> {
        logger.info("Updating expense with ID: $id")
        return try {
            val currentUser = getCurrentUserWithValidation()
            logger.debug("Current user3 ID: ${currentUser.id}")

            val existingExpense: ExpenseDto = expenseService.getExpenseById(id)
                ?: return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                    mapOf(
                        "success" to false,
                        "message" to "Expense not found",
                        "error" to "The specified expense does not exist"
                    )
                )
            validateExpenseAccess(existingExpense, currentUser, id)

            // Validate the updated expense data
            val validationErrors = validateExpenseData(expense)
            if (validationErrors.isNotEmpty()) {
                throw ExpenseValidationException("Expense validation failed", validationErrors)
            }

            if (!expense.familyId.isNullOrBlank()) {
                val family = familyRepository.findById(expense.familyId).orElse(null)
                if (family == null) {
                    logger.warn("User1 ${currentUser.id} attempted to update expense to non-existent family: ${expense.familyId}")
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                        mapOf(
                            "success" to false,
                            "message" to "Family not found",
                            "error" to "The specified family does not exist"
                        )
                    )
                }

                if (!family.membersIds.contains(currentUser.id) && family.headId != currentUser.id) {
                    logger.warn("User ${currentUser.id} attempted to update expense to family ${expense.familyId} but is not a member")
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                        mapOf(
                            "success" to false,
                            "message" to "You are not part of this family",
                            "error" to "Cannot update expense to a family you are not a member of"
                        )
                    )
                }

                logger.debug("Family membership validated for user ${currentUser.id} in family ${expense.familyId}")
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
            sendExpenseNotification(
                type = if (existingExpense.familyId == null || existingExpense.familyId.isBlank()) NotificationType.EXPENSE_UPDATED else NotificationType.FAMILY_EXPENSE_UPDATED,
                title = "Expense Updated",
                body = "${currentUser.name} updated expense: ${expense.description} - $${expense.amount}",
                expense = updatedExpense,
                user = currentUser,
                amount = expense.amount,
                description = expense.description
            )

            logger.info("Successfully updated expense $id for user ${currentUser.id}")
            ResponseEntity.ok(updatedExpense)
        } catch (e: ExpenseValidationException) {
            logger.error("Expense validation1 exception: ${e.message}")
            throw e
        } catch (_: NoSuchElementException) {
            logger.error("Expense not found for update: $id")
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            logger.error("Error updating expense $id: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteExpense(@PathVariable id: String): ResponseEntity<Any> {
        logger.info("Deleting expense with ID: $id")
        return try {
            val currentUser = getCurrentUserWithValidation()
            logger.debug("Current user1 ID: ${currentUser.id}")

            val existingExpense = expenseService.getExpenseById(id)
                ?: return ResponseEntity.notFound().build()

            if (!canDeleteExpense(existingExpense, currentUser)) {
                logger.warn("Access denied for user ${currentUser.id} trying to delete expense $id (owner: ${existingExpense.userId})")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }


            if (!existingExpense.familyId.isNullOrBlank()) {
                val family = familyRepository.findById(existingExpense.familyId).orElse(null)
                if (family == null) {
                    logger.warn("User ${currentUser.id} attempted to update expense to non-existent family: ${existingExpense.familyId}")
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                        mapOf(
                            "success" to false,
                            "message" to "Family not found",
                            "error" to "The specified family does not exist"
                        )
                    )
                }

                if (!family.membersIds.contains(currentUser.id) && family.headId != currentUser.id) {
                    logger.warn("User ${currentUser.id} attempted to update expense to family ${existingExpense.familyId} but is not a member")
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                        mapOf(
                            "success" to false,
                            "message" to "You are not part of this family",
                            "error" to "Cannot update expense to a family you are not a member of"
                        )
                    )
                }

                logger.debug("Family membership validated for user ${currentUser.id} in family ${existingExpense.familyId}")
            }

            if (expenseService.deleteExpense(id)) {
                sendExpenseNotification(
                    type = if (existingExpense.familyId == null || existingExpense.familyId.isBlank()) NotificationType.EXPENSE_DELETED else NotificationType.FAMILY_EXPENSE_DELETED,
                    title = "Expense Deleted",
                    body = "${currentUser.name} deleted expense: ${existingExpense.description} - $${existingExpense.amount}",
                    expense = existingExpense,
                    user = currentUser,
                    amount = existingExpense.amount,
                    description = existingExpense.description
                )
                logger.info("Successfully deleted expense $id for user ${currentUser.id} (original owner: ${existingExpense.userId})")
                ResponseEntity.ok(mapOf("message" to "Expense Deleted Successfully"))
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
        validateExpenseAccess(expense, currentUser, request.expenseId)

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

    @GetMapping("/family-monthly-sum")
    fun getFamilyMonthlyExpenseSum(
        @RequestParam year: Int,
        @RequestParam month: Int
    ): ResponseEntity<Map<String, Any>> {
        val currentUser = getCurrentUserWithValidation()
        val familyId = currentUser.familyId

        if (familyId == null || familyId.isBlank()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(
                mapOf(
                    "error" to "You are not part of any family",
                    "userId" to currentUser.id
                )
            )
        }

        if (year < 2000 || year > LocalDate.now().year) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "Invalid year. Year must be between 2000 and current year",
                    "year" to year
                )
            )
        }

        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "Invalid month. Month must be between 1 and 12",
                    "month" to month
                )
            )
        }

        val totalAmount = expenseService.getMonthlyExpenseSum(currentUser.id, year, month, familyId)

        return ResponseEntity.ok(
            mapOf(
                "year" to year,
                "month" to month,
                "totalAmount" to totalAmount,
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
