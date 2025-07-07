package com.lavish.expensetracker.service

import com.lavish.expensetracker.exception.DatabaseOperationException
import com.lavish.expensetracker.exception.ExpenseCreationException
import com.lavish.expensetracker.exception.ExpenseNotFoundException
import com.lavish.expensetracker.exception.ExpenseAccessDeniedException
import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.model.PagedResponse
import com.lavish.expensetracker.model.toDto
import com.lavish.expensetracker.model.toEntity
import com.lavish.expensetracker.repository.ExpenseJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.dao.DataAccessException
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

@Service
class ExpenseService(private val expenseRepository: ExpenseJpaRepository) {

    /**
     * Validates and sanitizes pagination parameters to prevent issues with dynamic page sizes
     */
    private fun validatePaginationParams(page: Int, size: Int): Pair<Int, Int> {
        val validatedPage = maxOf(0, page) // Ensure page is not negative
        val validatedSize = when {
            size <= 0 -> 10 // Default to 10 if size is 0 or negative
            size > 100 -> 100 // Cap at 100 to prevent performance issues
            else -> size
        }
        return Pair(validatedPage, validatedSize)
    }

    fun getAllExpenses(page: Int, size: Int): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val pageable = PageRequest.of(validatedPage, validatedSize)
        val result = expenseRepository.findAll(pageable)

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = validatedPage,
            size = validatedSize,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            isFirst = result.isFirst,
            isLast = result.isLast,
            hasNext = result.hasNext(),
            hasPrevious = result.hasPrevious()
        )
    }

    fun getExpensesWithOrder(
        page: Int,
        size: Int,
        sortBy: String = "expenseCreatedOn",
        isAsc: Boolean = false
    ): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)
        val pageable = PageRequest.of(validatedPage, validatedSize, sort)
        val result = expenseRepository.findAll(pageable)

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = validatedPage,
            size = validatedSize,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            isFirst = result.isFirst,
            isLast = result.isLast,
            hasNext = result.hasNext(),
            hasPrevious = result.hasPrevious()
        )
    }

    fun getExpensesByUserIdWithOrder(
        userId: String,
        page: Int,
        size: Int,
        sortBy: String = "date",
        isAsc: Boolean = false
    ): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)
        val pageable = PageRequest.of(validatedPage, validatedSize, sort)
        val result = expenseRepository.findByUserId(userId, pageable)

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = validatedPage,
            size = validatedSize,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            isFirst = result.isFirst,
            isLast = result.isLast,
            hasNext = result.hasNext(),
            hasPrevious = result.hasPrevious()
        )
    }

    fun getExpenseById(id: String): ExpenseDto? =
        expenseRepository.findById(id).map { it.toDto() }.orElse(null)

    fun createExpense(expense: ExpenseDto): ExpenseDto {
        return try {
            // Generate ID if not provided
            val expenseWithId = if (expense.expenseId.isBlank()) {
                expense.copy(expenseId = UUID.randomUUID().toString())
            } else {
                expense
            }

            // Additional validation before saving
            if (expenseWithId.userId.isBlank()) {
                throw ExpenseCreationException("User ID is required for expense creation")
            }

            if (expenseWithId.amount <= 0) {
                throw ExpenseCreationException("Amount must be greater than 0")
            }

            // Check for duplicate expense ID
            if (expenseRepository.existsById(expenseWithId.expenseId)) {
                throw ExpenseCreationException("An expense with ID '${expenseWithId.expenseId}' already exists")
            }

            val savedExpense = expenseRepository.save(expenseWithId.toEntity())
            savedExpense.toDto()

        } catch (e: ExpenseCreationException) {
            // Re-throw our custom exceptions
            throw e
        } catch (e: DataAccessException) {
            throw DatabaseOperationException(
                "Database error occurred while creating expense: ${e.message}",
                e
            )
        } catch (e: Exception) {
            throw ExpenseCreationException(
                "Unexpected error during expense creation: ${e.message}",
                e
            )
        }
    }

    fun updateExpense(id: String, expenseDetails: ExpenseDto): ExpenseDto {
        return try {
            val existingExpense = expenseRepository.findById(id)
                .orElseThrow { ExpenseNotFoundException("Expense not found with id: $id") }

            // Validate update data
            if (expenseDetails.amount <= 0) {
                throw ExpenseCreationException("Amount must be greater than 0")
            }

            if (expenseDetails.userId.isBlank()) {
                throw ExpenseCreationException("User ID cannot be empty")
            }

            val updatedExpense = existingExpense.copy(
                userId = expenseDetails.userId,
                amount = expenseDetails.amount,
                category = expenseDetails.category,
                description = expenseDetails.description,
                date = expenseDetails.date,
                familyId = expenseDetails.familyId.takeIf { it.isNotEmpty() },
                modifiedBy = expenseDetails.modifiedBy,
                lastModifiedOn = System.currentTimeMillis(),
                synced = expenseDetails.synced
            )

            expenseRepository.save(updatedExpense).toDto()

        } catch (e: ExpenseNotFoundException) {
            throw e
        } catch (e: ExpenseCreationException) {
            throw e
        } catch (e: DataAccessException) {
            throw DatabaseOperationException(
                "Database error occurred while updating expense: ${e.message}",
                e
            )
        } catch (e: Exception) {
            throw ExpenseCreationException(
                "Unexpected error during expense update: ${e.message}",
                e
            )
        }
    }

    fun deleteExpense(id: String): Boolean {
        return try {
            if (!expenseRepository.existsById(id)) {
                throw ExpenseNotFoundException("Expense with ID '$id' not found")
            }

            expenseRepository.deleteById(id)
            true

        } catch (e: ExpenseNotFoundException) {
            throw e
        } catch (e: DataAccessException) {
            throw DatabaseOperationException(
                "Database error occurred while deleting expense: ${e.message}",
                e
            )
        } catch (e: Exception) {
            throw ExpenseCreationException(
                "Unexpected error during expense deletion: ${e.message}",
                e
            )
        }
    }

    fun getExpensesByCategory(category: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val pageable = PageRequest.of(validatedPage, validatedSize)
        val result = expenseRepository.findByCategory(category, pageable)

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = validatedPage,
            size = validatedSize,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            isFirst = result.isFirst,
            isLast = result.isLast,
            hasNext = result.hasNext(),
            hasPrevious = result.hasPrevious()
        )
    }

    fun getExpensesByUserId(userId: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val pageable = PageRequest.of(validatedPage, validatedSize)
        val result = expenseRepository.findByUserId(userId, pageable)

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = validatedPage,
            size = validatedSize,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            isFirst = result.isFirst,
            isLast = result.isLast,
            hasNext = result.hasNext(),
            hasPrevious = result.hasPrevious()
        )
    }

    fun getExpensesByDateRange(startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val pageable = PageRequest.of(validatedPage, validatedSize)
        val result = expenseRepository.findByDateBetween(startDate, endDate, pageable)

        return createPagedResponse(result, validatedPage, validatedSize)
    }

    fun getExpensesByFamilyId(familyId: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val pageable = PageRequest.of(validatedPage, validatedSize)
        val result = expenseRepository.findByFamilyId(familyId, pageable)

        return createPagedResponse(result, validatedPage, validatedSize)
    }

    fun getExpensesByUserIdAndCategory(
        userId: String,
        category: String,
        page: Int,
        size: Int
    ): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val pageable = PageRequest.of(validatedPage, validatedSize)
        val result = expenseRepository.findByUserIdAndCategory(userId, category, pageable)

        return createPagedResponse(result, validatedPage, validatedSize)
    }

    fun getExpensesByUserIdAndDateRange(
        userId: String,
        startDate: Long,
        endDate: Long,
        page: Int,
        size: Int
    ): PagedResponse<ExpenseDto> {
        val (validatedPage, validatedSize) = validatePaginationParams(page, size)

        val pageable = PageRequest.of(validatedPage, validatedSize)
        val result = expenseRepository.findByUserIdAndDateBetween(userId, startDate, endDate, pageable)

        return createPagedResponse(result, validatedPage, validatedSize)
    }

    private fun createPagedResponse(
        result: org.springframework.data.domain.Page<com.lavish.expensetracker.model.Expense>,
        page: Int,
        size: Int
    ): PagedResponse<ExpenseDto> {
        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = page,
            size = size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            isFirst = result.isFirst,
            isLast = result.isLast,
            hasNext = result.hasNext(),
            hasPrevious = result.hasPrevious()
        )
    }

    fun getMonthlyExpenseSum(userId: String, year: Int, month: Int): Long {
        val yearMonth = YearMonth.of(year, month)
        val startDate = yearMonth.atDay(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
        val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000

        return expenseRepository.sumExpensesByUserIdAndDateRange(userId, startDate, endDate)
    }

    fun getExpensesByUserIdWithOffset(
        userId: String,
        offset: Int,
        size: Int,
        sortBy: String = "date",
        isAsc: Boolean = false
    ): PagedResponse<ExpenseDto> {
        val (validatedOffset, validatedSize) = validateOffsetParams(offset, size)

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)

        // Calculate page number from offset
        val pageNumber = validatedOffset / validatedSize
        val pageable = PageRequest.of(pageNumber, validatedSize, sort)

        // Get total count first to calculate proper pagination info
        val totalElements = expenseRepository.countByUserId(userId)

        // Use custom offset if it doesn't align with page boundaries
        val result = if (validatedOffset % validatedSize == 0) {
            // Offset aligns with page boundary, use standard pagination
            expenseRepository.findByUserId(userId, pageable)
        } else {
            // Offset doesn't align, we need to simulate offset-based pagination
            // Get a larger page and slice it
            val adjustedPageSize = validatedSize + (validatedOffset % validatedSize)
            val adjustedPageable = PageRequest.of(validatedOffset / validatedSize, adjustedPageSize, sort)
            val adjustedResult = expenseRepository.findByUserId(userId, adjustedPageable)

            // Slice the result to get exactly what we need
            val startIndex = validatedOffset % validatedSize
            val endIndex = minOf(startIndex + validatedSize, adjustedResult.content.size)
            val slicedContent = if (startIndex < adjustedResult.content.size) {
                adjustedResult.content.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            // Create a custom page result
            org.springframework.data.domain.PageImpl(
                slicedContent,
                pageable,
                totalElements
            )
        }

        val totalPages = ((totalElements + validatedSize - 1) / validatedSize).toInt()
        val currentPage = validatedOffset / validatedSize

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = currentPage,
            size = validatedSize,
            totalElements = totalElements,
            totalPages = totalPages,
            isFirst = validatedOffset == 0,
            isLast = validatedOffset + validatedSize >= totalElements,
            hasNext = validatedOffset + validatedSize < totalElements,
            hasPrevious = validatedOffset > 0,
            offset = validatedOffset
        )
    }

    /**
     * Validates offset and size parameters for offset-based pagination
     */
    private fun validateOffsetParams(offset: Int, size: Int): Pair<Int, Int> {
        val validatedOffset = maxOf(0, offset) // Ensure offset is not negative
        val validatedSize = when {
            size <= 0 -> 10 // Default to 10 if size is 0 or negative
            size > 100 -> 100 // Cap at 100 to prevent performance issues
            else -> size
        }
        return Pair(validatedOffset, validatedSize)
    }

    fun getExpensesByUserIdAfterCursor(
        userId: String,
        lastExpenseId: String,
        size: Int,
        sortBy: String = "date",
        isAsc: Boolean = false
    ): PagedResponse<ExpenseDto> {
        val validatedSize = when {
            size <= 0 -> 10
            size > 100 -> 100
            else -> size
        }

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)

        // Get the last expense to determine the cursor position
        val lastExpense = expenseRepository.findById(lastExpenseId).orElse(null)
            ?: throw ExpenseNotFoundException("Last expense with ID '$lastExpenseId' not found")

        // Verify the expense belongs to the user
        if (lastExpense.userId != userId) {
            throw ExpenseAccessDeniedException("Access denied to expense '$lastExpenseId'")
        }

        val pageable = PageRequest.of(0, validatedSize, sort)

        // Get expenses after the cursor based on the sort field
        val result = when (sortBy) {
            "date" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndDateGreaterThanOrderByDateAsc(userId, lastExpense.date, pageable)
                } else {
                    expenseRepository.findByUserIdAndDateLessThanOrderByDateDesc(userId, lastExpense.date, pageable)
                }
            }
            "amount" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndAmountGreaterThanOrderByAmountAsc(userId, lastExpense.amount, pageable)
                } else {
                    expenseRepository.findByUserIdAndAmountLessThanOrderByAmountDesc(userId, lastExpense.amount, pageable)
                }
            }
            "expenseCreatedOn" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndExpenseCreatedOnGreaterThanOrderByExpenseCreatedOnAsc(userId, lastExpense.expenseCreatedOn, pageable)
                } else {
                    expenseRepository.findByUserIdAndExpenseCreatedOnLessThanOrderByExpenseCreatedOnDesc(userId, lastExpense.expenseCreatedOn, pageable)
                }
            }
            else -> {
                // Default to date-based cursor
                if (isAsc) {
                    expenseRepository.findByUserIdAndDateGreaterThanOrderByDateAsc(userId, lastExpense.date, pageable)
                } else {
                    expenseRepository.findByUserIdAndDateLessThanOrderByDateDesc(userId, lastExpense.date, pageable)
                }
            }
        }

        // Get total count for pagination metadata
        val totalElements = expenseRepository.countByUserId(userId)

        // For cursor-based pagination, we calculate if there are more items
        val hasMore = result.content.size == validatedSize

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = 0, // Page concept doesn't apply to cursor-based pagination
            size = validatedSize,
            totalElements = totalElements,
            totalPages = -1, // Not applicable for cursor-based pagination
            isFirst = false, // Not the first page since we're using a cursor
            isLast = !hasMore,
            hasNext = hasMore,
            hasPrevious = true, // There are previous items since we have a cursor
            lastExpenseId = result.content.lastOrNull()?.expenseId // Include last expense ID for next cursor
        )
    }

    fun getExpensesSince(
        userId: String,
        lastModified: Long,
        size: Int,
        sortBy: String = "lastModifiedOn",
        isAsc: Boolean = true
    ): PagedResponse<ExpenseDto> {
        val validatedSize = when {
            size <= 0 -> 10
            size > 100 -> 100
            else -> size
        }

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)
        val pageable = PageRequest.of(0, validatedSize, sort)

        val result = when (sortBy) {
            "lastModifiedOn" -> expenseRepository.findByUserIdAndLastModifiedOnGreaterThan(userId, lastModified, pageable)
            "expenseCreatedOn" -> expenseRepository.findByUserIdAndExpenseCreatedOnGreaterThan(userId, lastModified, pageable)
            "date" -> expenseRepository.findByUserIdAndDateGreaterThan(userId, lastModified, pageable)
            else -> expenseRepository.findByUserIdAndLastModifiedOnGreaterThan(userId, lastModified, pageable)
        }

        val totalElements = expenseRepository.countByUserId(userId)
        val hasMore = result.content.size == validatedSize

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = 0,
            size = validatedSize,
            totalElements = totalElements,
            totalPages = -1, // Not applicable for time-based queries
            isFirst = true,
            isLast = !hasMore,
            hasNext = hasMore,
            hasPrevious = false,
            lastExpenseId = result.content.lastOrNull()?.expenseId
        )
    }

    fun getExpensesSinceWithCursor(
        userId: String,
        lastModified: Long,
        lastExpenseId: String,
        size: Int,
        sortBy: String = "lastModifiedOn",
        isAsc: Boolean = true
    ): PagedResponse<ExpenseDto> {
        val validatedSize = when {
            size <= 0 -> 10
            size > 100 -> 100
            else -> size
        }

        // Get the last expense to determine cursor position
        val lastExpense = expenseRepository.findById(lastExpenseId).orElse(null)
            ?: throw ExpenseNotFoundException("Last expense with ID '$lastExpenseId' not found")

        if (lastExpense.userId != userId) {
            throw ExpenseAccessDeniedException("Access denied to expense '$lastExpenseId'")
        }

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)
        val pageable = PageRequest.of(0, validatedSize, sort)

        // Get expenses after the cursor that are also after the lastModified timestamp
        val result = when (sortBy) {
            "lastModifiedOn" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnGreaterThan(
                        userId, lastModified, lastExpense.lastModifiedOn, pageable
                    )
                } else {
                    expenseRepository.findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnLessThan(
                        userId, lastModified, lastExpense.lastModifiedOn, pageable
                    )
                }
            }
            "expenseCreatedOn" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndExpenseCreatedOnGreaterThanAndExpenseCreatedOnGreaterThan(
                        userId, lastModified, lastExpense.expenseCreatedOn, pageable
                    )
                } else {
                    expenseRepository.findByUserIdAndExpenseCreatedOnGreaterThanAndExpenseCreatedOnLessThan(
                        userId, lastModified, lastExpense.expenseCreatedOn, pageable
                    )
                }
            }
            "date" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndDateGreaterThanAndDateGreaterThan(
                        userId, lastModified, lastExpense.date, pageable
                    )
                } else {
                    expenseRepository.findByUserIdAndDateGreaterThanAndDateLessThan(
                        userId, lastModified, lastExpense.date, pageable
                    )
                }
            }
            else -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnGreaterThan(
                        userId, lastModified, lastExpense.lastModifiedOn, pageable
                    )
                } else {
                    expenseRepository.findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnLessThan(
                        userId, lastModified, lastExpense.lastModifiedOn, pageable
                    )
                }
            }
        }

        val totalElements = expenseRepository.countByUserId(userId)
        val hasMore = result.content.size == validatedSize

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = 0,
            size = validatedSize,
            totalElements = totalElements,
            totalPages = -1,
            isFirst = false,
            isLast = !hasMore,
            hasNext = hasMore,
            hasPrevious = true,
            lastExpenseId = result.content.lastOrNull()?.expenseId
        )
    }

    fun getExpensesSinceDate(
        userId: String,
        sinceTimestamp: Long,
        size: Int,
        sortBy: String = "date",
        isAsc: Boolean = true
    ): PagedResponse<ExpenseDto> {
        val validatedSize = when {
            size <= 0 -> 10
            size > 100 -> 100
            else -> size
        }

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)
        val pageable = PageRequest.of(0, validatedSize, sort)

        val result = when (sortBy) {
            "date" -> expenseRepository.findByUserIdAndDateGreaterThanEqual(userId, sinceTimestamp, pageable)
            "lastModifiedOn" -> expenseRepository.findByUserIdAndLastModifiedOnGreaterThanEqual(userId, sinceTimestamp, pageable)
            "expenseCreatedOn" -> expenseRepository.findByUserIdAndExpenseCreatedOnGreaterThanEqual(userId, sinceTimestamp, pageable)
            "amount" -> expenseRepository.findByUserIdAndDateGreaterThanEqualOrderByAmount(userId, sinceTimestamp, pageable)
            else -> expenseRepository.findByUserIdAndDateGreaterThanEqual(userId, sinceTimestamp, pageable)
        }

        val totalElements = expenseRepository.countByUserId(userId)
        val hasMore = result.content.size == validatedSize

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = 0,
            size = validatedSize,
            totalElements = totalElements,
            totalPages = -1,
            isFirst = true,
            isLast = !hasMore,
            hasNext = hasMore,
            hasPrevious = false,
            lastExpenseId = result.content.lastOrNull()?.expenseId
        )
    }

    fun getExpensesSinceDateWithCursor(
        userId: String,
        sinceTimestamp: Long,
        lastExpenseId: String,
        size: Int,
        sortBy: String = "date",
        isAsc: Boolean = true
    ): PagedResponse<ExpenseDto> {
        val validatedSize = when {
            size <= 0 -> 10
            size > 100 -> 100
            else -> size
        }

        val lastExpense = expenseRepository.findById(lastExpenseId).orElse(null)
            ?: throw ExpenseNotFoundException("Last expense with ID '$lastExpenseId' not found")

        if (lastExpense.userId != userId) {
            throw ExpenseAccessDeniedException("Access denied to expense '$lastExpenseId'")
        }

        val direction = if (isAsc) Sort.Direction.ASC else Sort.Direction.DESC
        val sort = Sort.by(direction, sortBy)
        val pageable = PageRequest.of(0, validatedSize, sort)

        val result = when (sortBy) {
            "date" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndDateGreaterThanEqualAndDateGreaterThan(
                        userId, sinceTimestamp, lastExpense.date, pageable
                    )
                } else {
                    expenseRepository.findByUserIdAndDateGreaterThanEqualAndDateLessThan(
                        userId, sinceTimestamp, lastExpense.date, pageable
                    )
                }
            }
            "lastModifiedOn" -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndDateGreaterThanEqualAndLastModifiedOnGreaterThan(
                        userId, sinceTimestamp, lastExpense.lastModifiedOn, pageable
                    )
                } else {
                    expenseRepository.findByUserIdAndDateGreaterThanEqualAndLastModifiedOnLessThan(
                        userId, sinceTimestamp, lastExpense.lastModifiedOn, pageable
                    )
                }
            }
            else -> {
                if (isAsc) {
                    expenseRepository.findByUserIdAndDateGreaterThanEqualAndDateGreaterThan(
                        userId, sinceTimestamp, lastExpense.date, pageable
                    )
                } else {
                    expenseRepository.findByUserIdAndDateGreaterThanEqualAndDateLessThan(
                        userId, sinceTimestamp, lastExpense.date, pageable
                    )
                }
            }
        }

        val totalElements = expenseRepository.countByUserId(userId)
        val hasMore = result.content.size == validatedSize

        return PagedResponse(
            content = result.content.map { it.toDto() },
            page = 0,
            size = validatedSize,
            totalElements = totalElements,
            totalPages = -1,
            isFirst = false,
            isLast = !hasMore,
            hasNext = hasMore,
            hasPrevious = true,
            lastExpenseId = result.content.lastOrNull()?.expenseId
        )
    }
}
