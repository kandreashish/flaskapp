package com.example.expensetracker.util

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AuthUtil {

    fun getCurrentUserId(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication?.name ?: throw RuntimeException("User not authenticated")
    }

    fun isAuthenticated(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication != null && authentication.isAuthenticated
    }
}
