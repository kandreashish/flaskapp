package com.example.expensetracker.exception

class ExpenseCreationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ExpenseValidationException(message: String, val validationErrors: List<String>) : RuntimeException(message)

class ExpenseNotFoundException(message: String) : RuntimeException(message)

class ExpenseAccessDeniedException(message: String) : RuntimeException(message)

class DatabaseOperationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
