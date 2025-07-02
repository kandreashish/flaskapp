package com.example.expensetracker.repository

import com.example.expensetracker.model.ExpenseUser
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class UserRepository {

    private val users = ConcurrentHashMap<String, ExpenseUser>()

    fun save(user: ExpenseUser): ExpenseUser {
        users[user.id] = user
        return user
    }

    fun findByEmail(email: String): ExpenseUser? {
        return users.values.find { it.email == email }
    }

    fun findById(id: String): ExpenseUser? {
        return users[id]
    }

    fun existsByEmail(email: String): Boolean {
        return users.values.any { it.email == email }
    }

    fun findAll(): List<ExpenseUser> {
        return users.values.toList()
    }
}
