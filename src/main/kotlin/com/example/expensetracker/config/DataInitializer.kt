package com.example.expensetracker.config

import com.example.expensetracker.model.Expense
import com.example.expensetracker.repository.ExpenseRepository
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.LocalDate

@Configuration
@Profile("!test") // Don't run this in tests
open class DataInitializer(
    private val expenseRepository: ExpenseRepository
) {
    
    @PostConstruct
    fun init() {
        // Only add data if the database is empty
        if (expenseRepository.count() == 0L) {
            val expenses = listOf(
                Expense(
                    description = "Grocery shopping",
                    amount = 125.75,
                    category = "Groceries",
                    date = LocalDate.now().minusDays(2)
                ),
                Expense(
                    description = "Monthly Netflix subscription",
                    amount = 15.99,
                    category = "Entertainment",
                    date = LocalDate.now().minusDays(5)
                ),
                Expense(
                    description = "Lunch with team",
                    amount = 32.50,
                    category = "Food",
                    date = LocalDate.now().minusDays(1)
                ),
                Expense(
                    description = "Electric bill",
                    amount = 85.20,
                    category = "Utilities",
                    date = LocalDate.now().minusDays(10)
                ),
                Expense(
                    description = "New headphones",
                    amount = 129.99,
                    category = "Electronics",
                    date = LocalDate.now().minusDays(3)
                ),
                Expense(
                    description = "Coffee shop",
                    amount = 4.75,
                    category = "Food",
                    date = LocalDate.now()
                ),
                Expense(
                    description = "Gym membership",
                    amount = 45.00,
                    category = "Health",
                    date = LocalDate.now().withDayOfMonth(1) // First of this month
                ),
                Expense(
                    description = "Taxi to airport",
                    amount = 28.50,
                    category = "Transportation",
                    date = LocalDate.now().minusDays(7)
                )
            )
            
            expenseRepository.saveAll(expenses)
            println("Added ${expenses.size} sample expenses to the database")
        }
    }
}
