package com.example.expensetracker.config

import com.example.expensetracker.model.ExpenseDto
import com.example.expensetracker.repository.ExpenseRepositoryImpl
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.*

@Configuration
@Profile("!test") // Don't run this in tests
open class DataInitializer(
    private val expenseRepository: ExpenseRepositoryImpl
) {
    
    @PostConstruct
    fun init() {
        // Only add data if the database is empty
        if (expenseRepository.isEmpty()) {
            val now = System.currentTimeMillis()
            val expenses = listOf(
                ExpenseDto(
                    expenseId = UUID.randomUUID().toString(),
                    userId = "user1",
                    description = "Grocery shopping",
                    amount = 12575, // Amount in cents
                    category = "GROCERIES",
                    date = now,
                    familyId = "family1",
                    createdBy = "user1",
                    expenseCreatedOn = now,
                    lastModifiedOn = now
                ),
                ExpenseDto(
                    expenseId = UUID.randomUUID().toString(),
                    userId = "user1",
                    description = "Monthly Netflix subscription",
                    amount = 1599,
                    category = "ENTERTAINMENT",
                    date = now,
                    familyId = "family1",
                    createdBy = "user1",
                    expenseCreatedOn = now,
                    lastModifiedOn = now
                ),
                ExpenseDto(
                    expenseId = UUID.randomUUID().toString(),
                    userId = "user1",
                    description = "Lunch with team",
                    amount = 3250,
                    category = "FOOD",
                    date = now,
                    familyId = "family1",
                    createdBy = "user1",
                    expenseCreatedOn = now,
                    lastModifiedOn = now
                ),
                ExpenseDto(
                    expenseId = UUID.randomUUID().toString(),
                    userId = "user1",
                    description = "Electric bill",
                    amount = 8520,
                    category = "UTILITIES",
                    date = now,
                    familyId = "family1",
                    createdBy = "user1",
                    expenseCreatedOn = now,
                    lastModifiedOn = now
                ),
                ExpenseDto(
                    expenseId = UUID.randomUUID().toString(),
                    userId = "user1",
                    description = "New headphones",
                    amount = 12999,
                    category = "ELECTRONICS",
                    date = now,
                    familyId = "family1",
                    createdBy = "user1",
                    expenseCreatedOn = now,
                    lastModifiedOn = now
                )
            )
            
            expenses.forEach { expenseRepository.save(it) }
            println("Added ${expenses.size} sample expenses to the database")
        }
    }
}
