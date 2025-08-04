package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.model.PagedResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import org.springframework.data.domain.Page

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

    override fun deleteByFamilyId(familyId: String): Int {
        val toDelete = expenses.values.filter { it.familyId == familyId }
        toDelete.forEach { expenses.remove(it.expenseId) }
        return toDelete.size
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
            it.userId == userId && it.date in startDate..endDate && !it.deleted
        }.sortedByDescending { it.expenseCreatedOn }
        return createPagedResponse(filteredExpenses, page, size)
    }

    override fun findByFamilyIdOrUserFamilyId(familyId: String, pageable: Pageable): Page<ExpenseDto> {
        // Filter for non-deleted expenses only
        val filteredExpenses = expenses.values.filter { expense ->
            expense.familyId == familyId && !expense.deleted
        }

        // Apply sorting from pageable
        val sortedExpenses = when {
            pageable.sort.isSorted -> {
                val sortOrder = pageable.sort.first()
                when (sortOrder?.property) {
                    "date" -> if (sortOrder.isAscending) filteredExpenses.sortedBy { it.date }
                             else filteredExpenses.sortedByDescending { it.date }
                    "amount" -> if (sortOrder.isAscending) filteredExpenses.sortedBy { it.amount }
                               else filteredExpenses.sortedByDescending { it.amount }
                    "expenseCreatedOn" -> if (sortOrder.isAscending) filteredExpenses.sortedBy { it.expenseCreatedOn }
                                         else filteredExpenses.sortedByDescending { it.expenseCreatedOn }
                    else -> filteredExpenses.sortedByDescending { it.date }
                }
            }
            else -> filteredExpenses.sortedByDescending { it.date }
        }

        // Apply pagination
        val totalElements = sortedExpenses.size.toLong()
        val startIndex = pageable.offset.toInt()
        val endIndex = minOf(startIndex + pageable.pageSize, sortedExpenses.size)

        val content = if (startIndex < sortedExpenses.size) {
            sortedExpenses.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Return a simple Page implementation using Spring's PageImpl
        return org.springframework.data.domain.PageImpl(content, pageable, totalElements)
    }

    // New methods for soft delete and "since" functionality

    // Methods for "since" queries (include deleted expenses for sync)
    override fun findByFamilyIdAndLastModifiedOnGreaterThan(familyId: String, lastModified: Long, pageable: Pageable): Page<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { expense ->
            expense.familyId == familyId && expense.lastModifiedOn > lastModified
        }
        return createPageFromList(filteredExpenses, pageable)
    }

    override fun findByFamilyIdAndExpenseCreatedOnGreaterThan(familyId: String, expenseCreatedOn: Long, pageable: Pageable): Page<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { expense ->
            expense.familyId == familyId && expense.expenseCreatedOn > expenseCreatedOn
        }
        return createPageFromList(filteredExpenses, pageable)
    }

    override fun findByFamilyIdAndDateGreaterThanEqual(familyId: String, date: Long, pageable: Pageable): Page<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { expense ->
            expense.familyId == familyId && expense.date >= date
        }
        return createPageFromList(filteredExpenses, pageable)
    }

    fun findByUserIdAndLastModifiedOnGreaterThan(userId: String, lastModified: Long, pageable: Pageable): Page<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { expense ->
            expense.userId == userId && expense.lastModifiedOn > lastModified
        }
        return createPageFromList(filteredExpenses, pageable)
    }

    fun findByUserIdAndExpenseCreatedOnGreaterThan(userId: String, createdOn: Long, pageable: Pageable): Page<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { expense ->
            expense.userId == userId && expense.expenseCreatedOn > createdOn
        }
        return createPageFromList(filteredExpenses, pageable)
    }

    fun findByUserIdAndDateGreaterThan(userId: String, date: Long, pageable: Pageable): Page<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { expense ->
            expense.userId == userId && expense.date > date
        }
        return createPageFromList(filteredExpenses, pageable)
    }

    fun findByUserIdAndDateGreaterThanEqual(userId: String, date: Long, pageable: Pageable): Page<ExpenseDto> {
        val filteredExpenses = expenses.values.filter { expense ->
            expense.userId == userId && expense.date >= date
        }
        return createPageFromList(filteredExpenses, pageable)
    }

    // Count methods
    fun countByUserId(userId: String): Long {
        return expenses.values.count { it.userId == userId && !it.deleted }.toLong()
    }

    fun countByFamilyId(familyId: String): Long {
        return expenses.values.count { it.familyId == familyId && !it.deleted }.toLong()
    }

    // Helper method to create Page from list with proper sorting
    private fun createPageFromList(items: Collection<ExpenseDto>, pageable: Pageable): Page<ExpenseDto> {
        val sortedItems = when {
            pageable.sort.isSorted -> {
                val sortOrder = pageable.sort.first()
                when (sortOrder?.property) {
                    "date" -> if (sortOrder.isAscending) items.sortedBy { it.date }
                             else items.sortedByDescending { it.date }
                    "amount" -> if (sortOrder.isAscending) items.sortedBy { it.amount }
                               else items.sortedByDescending { it.amount }
                    "expenseCreatedOn" -> if (sortOrder.isAscending) items.sortedBy { it.expenseCreatedOn }
                                         else items.sortedByDescending { it.expenseCreatedOn }
                    "lastModifiedOn" -> if (sortOrder.isAscending) items.sortedBy { it.lastModifiedOn }
                                       else items.sortedByDescending { it.lastModifiedOn }
                    else -> items.sortedByDescending { it.lastModifiedOn }
                }
            }
            else -> items.sortedByDescending { it.lastModifiedOn }
        }

        val totalElements = sortedItems.size.toLong()
        val startIndex = pageable.offset.toInt()
        val endIndex = minOf(startIndex + pageable.pageSize, sortedItems.size)

        val content = if (startIndex < sortedItems.size) {
            sortedItems.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        return org.springframework.data.domain.PageImpl(content, pageable, totalElements)
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
