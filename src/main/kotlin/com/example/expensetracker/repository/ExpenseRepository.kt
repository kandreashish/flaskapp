package com.example.expensetracker.repository

import com.example.expensetracker.model.Expense
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ExpenseRepository : JpaRepository<Expense, Long> {
    fun findByCategory(category: String): List<Expense>
    fun findByDateBetween(startDate: java.time.LocalDate, endDate: java.time.LocalDate): List<Expense>
}
