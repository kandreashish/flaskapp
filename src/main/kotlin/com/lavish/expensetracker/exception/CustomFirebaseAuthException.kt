package com.lavish.expensetracker.exception

/**
 * Custom exception for Firebase authentication errors
 */
class CustomFirebaseAuthException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
