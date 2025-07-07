package com.lavish.expensetracker.util

import com.lavish.expensetracker.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class AuthUtil(
    private val userService: UserService
) {

    fun getCurrentUserId(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = authentication?.principal as? String
            ?: throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required. Please provide a valid JWT token."
            )

        // Verify that the user actually exists in the database
        if (!userService.userExists(userId)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "User account not found or has been deactivated. Please re-authenticate."
            )
        }

        return userId
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

    fun validateUserAccess(userId: String): Boolean {
        val currentUserId = getCurrentUserId()
        return currentUserId == userId
    }
}
