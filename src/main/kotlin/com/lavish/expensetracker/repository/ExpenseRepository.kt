package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.model.PagedResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
interface ExpenseRepository {
    fun findAll(page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findById(id: String): ExpenseDto?
    fun save(expense: ExpenseDto): ExpenseDto
    fun deleteById(id: String): Boolean
    fun deleteByFamilyId(familyId: String): Int
    fun findByCategory(category: String, page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findByDateBetween(startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findByUserId(userId: String, page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findByFamilyId(familyId: String, page: Int, size: Int): PagedResponse<ExpenseDto>
    // New authentication-aware methods
    fun findByUserIdAndCategory(userId: String, category: String, page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findByUserIdAndDateBetween(userId: String, startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto>

    // Missing method for family expenses with Spring Data pagination
    fun findByFamilyIdOrUserFamilyId(familyId: String, pageable: Pageable): Page<ExpenseDto>

    // New methods for family expenses since timestamp
    fun findByFamilyIdAndLastModifiedOnGreaterThan(familyId: String, lastModified: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdAndExpenseCreatedOnGreaterThan(familyId: String, expenseCreatedOn: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdAndDateGreaterThanEqual(familyId: String, date: Long, pageable: Pageable): Page<ExpenseDto>

    // Methods for user expenses since timestamp
    fun findByUserIdAndLastModifiedOnGreaterThan(userId: String, lastModified: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByUserIdAndExpenseCreatedOnGreaterThan(userId: String, createdOn: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByUserIdAndDateGreaterThan(userId: String, date: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByUserIdAndDateGreaterThanEqual(userId: String, date: Long, pageable: Pageable): Page<ExpenseDto>

    // Count methods
    fun countByUserId(userId: String): Long
    fun countByFamilyId(familyId: String): Long
    fun countByFamilyIdOrUserFamilyId(familyId: String): Long

    // Cursor-based pagination methods with delete filtering
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndDateGreaterThanOrderByDateAsc(familyId: String, cursorValue: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndDateLessThanOrderByDateDesc(familyId: String, cursorValue: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndExpenseCreatedOnGreaterThanOrderByExpenseCreatedOnAsc(familyId: String, cursorValue: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndExpenseCreatedOnLessThanOrderByExpenseCreatedOnDesc(familyId: String, cursorValue: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndLastModifiedOnGreaterThanOrderByLastModifiedOnAsc(familyId: String, cursorValue: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndLastModifiedOnLessThanOrderByLastModifiedOnDesc(familyId: String, cursorValue: Long, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndAmountGreaterThanOrderByAmountAsc(familyId: String, cursorValue: Int, pageable: Pageable): Page<ExpenseDto>
    fun findByFamilyIdOrUserFamilyIdAndDeleteFalseAndAmountLessThanOrderByAmountDesc(familyId: String, cursorValue: Int, pageable: Pageable): Page<ExpenseDto>
}
