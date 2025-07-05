package com.example.expensetracker.service

import com.example.expensetracker.exception.DatabaseOperationException
import com.example.expensetracker.exception.ExpenseCreationException
import com.example.expensetracker.exception.ExpenseNotFoundException
import com.example.expensetracker.model.ExpenseDto
import com.example.expensetracker.model.PagedResponse
import com.example.expensetracker.model.toDto
import com.example.expensetracker.model.toEntity
import com.example.expensetracker.repository.ExpenseJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.dao.DataAccessException
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

@Service
class ExpenseService(private val expenseRepository: ExpenseJpaRepository) {

    fun getAllExpenses(page: Int, size: Int): PagedResponse<ExpenseDto> {
        val pageable = PageRequest.of(page, size)
        val result = expenseRepository.findAll(pageable)

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
        val pageable = PageRequest.of(page, size)
        val result = expenseRepository.findByCategory(category, pageable)

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

    fun getExpensesByUserId(userId: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val pageable = PageRequest.of(page, size)
        val result = expenseRepository.findByUserId(userId, pageable)

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

    fun getExpensesByDateRange(startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val pageable = PageRequest.of(page, size)
        val result = expenseRepository.findByDateBetween(startDate, endDate, pageable)

        return createPagedResponse(result, page, size)
    }

    fun getExpensesByFamilyId(familyId: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val pageable = PageRequest.of(page, size)
        val result = expenseRepository.findByFamilyId(familyId, pageable)

        return createPagedResponse(result, page, size)
    }

    fun getExpensesByUserIdAndCategory(userId: String, category: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val pageable = PageRequest.of(page, size)
        val result = expenseRepository.findByUserIdAndCategory(userId, category, pageable)

        return createPagedResponse(result, page, size)
    }

    fun getExpensesByUserIdAndDateRange(userId: String, startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val pageable = PageRequest.of(page, size)
        val result = expenseRepository.findByUserIdAndDateBetween(userId, startDate, endDate, pageable)

        return createPagedResponse(result, page, size)
    }

    private fun createPagedResponse(result: org.springframework.data.domain.Page<com.example.expensetracker.model.Expense>, page: Int, size: Int): PagedResponse<ExpenseDto> {
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
}
