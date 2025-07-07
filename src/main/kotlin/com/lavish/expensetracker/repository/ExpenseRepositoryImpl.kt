package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.model.PagedResponse
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

@Repository
class ExpenseRepositoryImpl : ExpenseRepository {

    private val expenses = ConcurrentHashMap<String, ExpenseDto>()

    override fun findAll(page: Int, size: Int): PagedResponse<ExpenseDto> {
        val allExpenses = expenses.values.toList().sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(allExpenses, page, size)
    }

    override fun findById(id: String): ExpenseDto? {
        return expenses[id]
    }

    override fun save(expense: ExpenseDto): ExpenseDto {
        expenses[expense.expenseId] = expense
        return expense
    }

    override fun deleteById(id: String): Boolean {
        return expenses.remove(id) != null
    }

    override fun findByCategory(category: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { it.category == category }
            .sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(filteredExpenses, page, size)
    }

    override fun findByDateBetween(startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { it.date in startDate..endDate }
            .sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(filteredExpenses, page, size)
    }

    override fun findByUserId(userId: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { it.userId == userId }
            .sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(filteredExpenses, page, size)
    }

    override fun findByFamilyId(familyId: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { it.familyId == familyId }
            .sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(filteredExpenses, page, size)
    }

    override fun findByUserIdAndCategory(userId: String, category: String, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val filteredExpenses = expenses.values.filter {
            it.userId == userId && it.category == category
        }.sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(filteredExpenses, page, size)
    }

    override fun findByUserIdAndDateBetween(userId: String, startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val filteredExpenses = expenses.values.filter {
            it.userId == userId && it.date in startDate..endDate
        }.sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(filteredExpenses, page, size)
    }

    // Helper method to check if repository has data (for DataInitializer)
    fun isEmpty(): Boolean = expenses.isEmpty()

    private fun createPagedResponse(allItems: List<ExpenseDto>, page: Int, size: Int): PagedResponse<ExpenseDto> {
        val totalElements = allItems.size.toLong()
        val totalPages = ceil(totalElements.toDouble() / size).toInt()
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, allItems.size)

        val content = if (startIndex < allItems.size) {
            allItems.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        return PagedResponse(
            content = content,
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            isFirst = page == 0,
            isLast = page >= totalPages - 1,
            hasNext = page < totalPages - 1,
            hasPrevious = page > 0
        )
    }
}
