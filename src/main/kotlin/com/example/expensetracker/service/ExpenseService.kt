package com.example.expensetracker.service

import com.example.expensetracker.model.ExpenseDto
import com.example.expensetracker.model.PagedResponse
import com.example.expensetracker.model.toDto
import com.example.expensetracker.model.toEntity
import com.example.expensetracker.repository.ExpenseJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
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
        // Generate ID if not provided
        val expenseWithId = if (expense.expenseId.isBlank()) {
            expense.copy(expenseId = UUID.randomUUID().toString())
        } else {
            expense
        }
        return expenseRepository.save(expenseWithId.toEntity()).toDto()
    }

    fun updateExpense(id: String, expenseDetails: ExpenseDto): ExpenseDto {
        val existingExpense = expenseRepository.findById(id)
            .orElseThrow { NoSuchElementException("Expense not found with id: $id") }

        val updatedExpense = existingExpense.copy(
            userId = expenseDetails.userId,
            amount = expenseDetails.amount,
            category = expenseDetails.category,
            description = expenseDetails.description,
            date = expenseDetails.date,
            familyId = expenseDetails.familyId.takeIf { it.isNotEmpty() },
            dateExpense = expenseDetails.dateExpense,
            modifiedBy = expenseDetails.modifiedBy,
            lastModifiedOn = System.currentTimeMillis(),
            synced = expenseDetails.synced
        )
        return expenseRepository.save(updatedExpense).toDto()
    }

    fun deleteExpense(id: String): Boolean {
        return if (expenseRepository.existsById(id)) {
            expenseRepository.deleteById(id)
            true
        } else {
            false
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
}
