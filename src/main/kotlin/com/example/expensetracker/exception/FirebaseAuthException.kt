package com.example.expensetracker.exception

/**
 * Exception thrown when there is an error during Firebase authentication operations.
 *
 * @property message The detail message
 * @property cause The underlying cause of this exception
 */
class CustomFirebaseAuthException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)
