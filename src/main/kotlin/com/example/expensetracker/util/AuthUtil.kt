package com.example.expensetracker.util

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AuthUtil {

    fun getCurrentUserId(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication?.principal as? String
            ?: throw IllegalStateException("No authenticated user found")
    }

    fun isAuthenticated(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication != null && authentication.isAuthenticated
    }

    fun getCurrentUserIdOrNull(): String? {
        return try {
            getCurrentUserId()
        } catch (e: Exception) {
            null
        }
    }
}
