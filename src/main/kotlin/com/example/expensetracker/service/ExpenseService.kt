package com.example.expensetracker.service

import com.example.expensetracker.model.ExpenseDto
import com.example.expensetracker.model.PagedResponse
import com.example.expensetracker.repository.ExpenseRepository
import org.springframework.stereotype.Service

@Service
class ExpenseService(private val expenseRepository: ExpenseRepository) {

    fun getAllExpenses(page: Int, size: Int): PagedResponse<ExpenseDto> =
        expenseRepository.findAll(page, size)

    fun getExpenseById(id: String): ExpenseDto? = expenseRepository.findById(id)

    fun createExpense(expense: ExpenseDto): ExpenseDto = expenseRepository.save(expense)

    fun updateExpense(id: String, expenseDetails: ExpenseDto): ExpenseDto {
        val existingExpense = expenseRepository.findById(id)
            ?: throw NoSuchElementException("Expense not found with id: $id")

        val updatedExpense = existingExpense.copy(
            userId = expenseDetails.userId,
            amount = expenseDetails.amount,
            category = expenseDetails.category,
            description = expenseDetails.description,
            date = expenseDetails.date,
            familyId = expenseDetails.familyId,
            dateExpense = expenseDetails.dateExpense,
            modifiedBy = expenseDetails.modifiedBy,
            lastModifiedOn = System.currentTimeMillis(),
            synced = expenseDetails.synced
        )
        return expenseRepository.save(updatedExpense)
    }

    fun deleteExpense(id: String): Boolean = expenseRepository.deleteById(id)

    fun getExpensesByCategory(category: String, page: Int, size: Int): PagedResponse<ExpenseDto> =
        expenseRepository.findByCategory(category, page, size)

    fun getExpensesBetweenDates(startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto> =
        expenseRepository.findByDateBetween(startDate, endDate, page, size)

    fun getExpensesByUserId(userId: String, page: Int, size: Int): PagedResponse<ExpenseDto> =
        expenseRepository.findByUserId(userId, page, size)

    fun getExpensesByFamilyId(familyId: String, page: Int, size: Int): PagedResponse<ExpenseDto> =
        expenseRepository.findByFamilyId(familyId, page, size)

    // New authentication-aware methods
    fun getExpensesByUserIdAndCategory(userId: String, category: String, page: Int, size: Int): PagedResponse<ExpenseDto> =
        expenseRepository.findByUserIdAndCategory(userId, category, page, size)

    fun getExpensesByUserIdAndDateRange(userId: String, startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto> =
        expenseRepository.findByUserIdAndDateBetween(userId, startDate, endDate, page, size)
}
