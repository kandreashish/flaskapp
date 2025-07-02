package com.example.expensetracker.controller

import com.example.expensetracker.model.Expense
import com.example.expensetracker.service.ExpenseService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/expenses")
class ExpenseController(private val expenseService: ExpenseService) {

    @GetMapping
    fun getAllExpenses(): List<Expense> = expenseService.getAllExpenses()

    @GetMapping("/{id}")
    fun getExpenseById(@PathVariable id: Long): ResponseEntity<Expense> {
        return expenseService.getExpenseById(id)?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.notFound().build()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(@RequestBody expense: Expense): Expense = expenseService.createExpense(expense)

    @PutMapping("/{id}")
    fun updateExpense(@PathVariable id: Long, @RequestBody expense: Expense): ResponseEntity<Expense> {
        return try {
            val updatedExpense = expenseService.updateExpense(id, expense)
            ResponseEntity.ok(updatedExpense)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteExpense(@PathVariable id: Long): ResponseEntity<Void> {
        return if (expenseService.deleteExpense(id)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/category/{category}")
    fun getExpensesByCategory(@PathVariable category: String): List<Expense> =
        expenseService.getExpensesByCategory(category)

    @GetMapping("/between-dates")
    fun getExpensesBetweenDates(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): List<Expense> {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        return expenseService.getExpensesBetweenDates(start, end)
    }
}
