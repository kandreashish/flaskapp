package com.example.expensetracker.config

import com.example.expensetracker.model.ExpenseUser
import com.example.expensetracker.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.util.*

@Component
class UserDataInitializer(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @PostConstruct
    fun init() {
        // Create default test users if no users exist
        if (userRepository.findAll().isEmpty()) {
            val familyId = UUID.randomUUID().toString()

            val testUsers = listOf(
                ExpenseUser(
                    id = UUID.randomUUID().toString(),
                    name = "John Doe",
                    email = "john@example.com",
                    password = passwordEncoder.encode("password123"),
                    familyId = familyId,
                    updatedAt = System.currentTimeMillis(),
                    roles = listOf("USER")
                ),
                ExpenseUser(
                    id = UUID.randomUUID().toString(),
                    name = "Jane Doe",
                    email = "jane@example.com",
                    password = passwordEncoder.encode("password123"),
                    familyId = familyId,
                    updatedAt = System.currentTimeMillis(),
                    roles = listOf("USER")
                )
            )

            testUsers.forEach { userRepository.save(it) }
            println("Added ${testUsers.size} test users to the database")
            println("Login credentials: john@example.com / password123 or jane@example.com / password123")
        }
    }
}
