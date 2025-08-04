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
}
