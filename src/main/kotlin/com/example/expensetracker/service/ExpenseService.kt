package com.example.expensetracker.service

import com.example.expensetracker.model.Expense
import com.example.expensetracker.repository.ExpenseRepository
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ExpenseService(private val expenseRepository: ExpenseRepository) {

    fun getAllExpenses(): List<Expense> = expenseRepository.findAll()

    fun getExpenseById(id: Long): Expense? = expenseRepository.findById(id).orElse(null)

    fun createExpense(expense: Expense): Expense = expenseRepository.save(expense)

    fun updateExpense(id: Long, expenseDetails: Expense): Expense {
        return expenseRepository.findById(id).map { existingExpense ->
            val updatedExpense = existingExpense.copy(
                description = expenseDetails.description,
                amount = expenseDetails.amount,
                category = expenseDetails.category,
                date = expenseDetails.date,
                notes = expenseDetails.notes
            )
            expenseRepository.save(updatedExpense)
        }.orElseThrow { NoSuchElementException("Expense not found with id: $id") }
    }

    fun deleteExpense(id: Long): Boolean {
        return try {
            expenseRepository.deleteById(id)
            true
        } catch (e: EmptyResultDataAccessException) {
            false
        }
    }

    fun getExpensesByCategory(category: String): List<Expense> = 
        expenseRepository.findByCategory(category)

    fun getExpensesBetweenDates(startDate: LocalDate, endDate: LocalDate): List<Expense> =
        expenseRepository.findByDateBetween(startDate, endDate)
}
