package com.example.expensetracker.controller

import com.example.expensetracker.model.ExpenseDto
import com.example.expensetracker.model.PagedResponse
import com.example.expensetracker.service.ExpenseService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/expenses")
class ExpenseController(private val expenseService: ExpenseService) {

    @GetMapping
    fun getAllExpenses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        return expenseService.getAllExpenses(page, size)
    }

    @GetMapping("/{id}")
    fun getExpenseById(@PathVariable id: String): ResponseEntity<ExpenseDto> {
        return expenseService.getExpenseById(id)?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.notFound().build()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExpense(@RequestBody expense: ExpenseDto): ExpenseDto = expenseService.createExpense(expense)

    @PutMapping("/{id}")
    fun updateExpense(@PathVariable id: String, @RequestBody expense: ExpenseDto): ResponseEntity<ExpenseDto> {
        return try {
            val updatedExpense = expenseService.updateExpense(id, expense)
            ResponseEntity.ok(updatedExpense)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteExpense(@PathVariable id: String): ResponseEntity<Void> {
        return if (expenseService.deleteExpense(id)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/category/{category}")
    fun getExpensesByCategory(
        @PathVariable category: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> = expenseService.getExpensesByCategory(category, page, size)

    @GetMapping("/between-dates")
    fun getExpensesBetweenDates(
        @RequestParam startDate: String,
        @RequestParam endDate: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> {
        // Convert ISO date strings to epoch timestamps
        val start = LocalDate.parse(startDate).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
        val end = LocalDate.parse(endDate).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
        return expenseService.getExpensesBetweenDates(start, end, page, size)
    }

    @GetMapping("/user/{userId}")
    fun getExpensesByUserId(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> = expenseService.getExpensesByUserId(userId, page, size)

    @GetMapping("/family/{familyId}")
    fun getExpensesByFamilyId(
        @PathVariable familyId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): PagedResponse<ExpenseDto> = expenseService.getExpensesByFamilyId(familyId, page, size)
}
