package com.example.expensetracker.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DataAccessException
import java.time.LocalDateTime

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = ex.statusCode.value(),
            error = ex.statusCode.toString().split(" ").last(),
            message = ex.reason ?: "An error occurred",
            path = null
        )
        return ResponseEntity(errorResponse, ex.statusCode)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 400,
            error = "Bad Request",
            message = ex.message ?: "Invalid request parameters",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 404,
            error = "Not Found",
            message = ex.message ?: "Resource not found",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 500,
            error = "Internal Server Error",
            message = "An unexpected error occurred. Please try again later.",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(ExpenseCreationException::class)
    fun handleExpenseCreationException(ex: ExpenseCreationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 500,
            error = "Expense Creation Failed",
            message = ex.message ?: "Failed to create expense. Please try again.",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(ExpenseValidationException::class)
    fun handleExpenseValidationException(ex: ExpenseValidationException): ResponseEntity<ValidationErrorResponse> {
        val errorResponse = ValidationErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 400,
            error = "Validation Failed",
            message = ex.message ?: "Please fix the validation errors",
            validationErrors = ex.validationErrors,
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(ExpenseNotFoundException::class)
    fun handleExpenseNotFoundException(ex: ExpenseNotFoundException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 404,
            error = "Expense Not Found",
            message = ex.message ?: "The requested expense could not be found",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(ExpenseAccessDeniedException::class)
    fun handleExpenseAccessDeniedException(ex: ExpenseAccessDeniedException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 403,
            error = "Access Denied",
            message = ex.message ?: "You don't have permission to access this expense",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(DatabaseOperationException::class)
    fun handleDatabaseOperationException(ex: DatabaseOperationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 500,
            error = "Database Operation Failed",
            message = ex.message ?: "A database error occurred. Please try again later.",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 400,
            error = "Data Integrity Violation",
            message = "The operation violates data constraints. Please check your input data.",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccessException(ex: DataAccessException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 500,
            error = "Database Access Error",
            message = "Unable to access the database. Please try again later.",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now().toString(),
            status = 400,
            error = "Invalid JSON Format",
            message = "The request body contains invalid JSON or missing required fields. Please check your request format.",
            path = null
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }
}

data class ErrorResponse(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val path: String? = null,
    val trace: String? = null // Optional for debugging purposes
)

data class ValidationErrorResponse(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val validationErrors: List<String>,
    val path: String? = null
)
