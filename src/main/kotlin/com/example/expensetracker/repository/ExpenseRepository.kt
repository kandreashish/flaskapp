package com.example.expensetracker.repository

import com.example.expensetracker.model.ExpenseDto
import com.example.expensetracker.model.PagedResponse
import org.springframework.stereotype.Repository

@Repository
interface ExpenseRepository {
    fun findAll(page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findById(id: String): ExpenseDto?
    fun save(expense: ExpenseDto): ExpenseDto
    fun deleteById(id: String): Boolean
    fun findByCategory(category: String, page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findByDateBetween(startDate: Long, endDate: Long, page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findByUserId(userId: String, page: Int, size: Int): PagedResponse<ExpenseDto>
    fun findByFamilyId(familyId: String, page: Int, size: Int): PagedResponse<ExpenseDto>
}
