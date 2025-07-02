package com.example.expensetracker.repository

import com.example.expensetracker.model.Expense
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ExpenseJpaRepository : JpaRepository<Expense, String> {
    fun findByUserId(userId: String, pageable: Pageable): Page<Expense>
    fun findByCategory(category: String, pageable: Pageable): Page<Expense>
    fun findByDateBetween(startDate: Long, endDate: Long, pageable: Pageable): Page<Expense>
    fun findByFamilyId(familyId: String, pageable: Pageable): Page<Expense>
    fun findByUserIdAndCategory(userId: String, category: String, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateBetween(userId: String, startDate: Long, endDate: Long, pageable: Pageable): Page<Expense>
}
