package com.lavish.expensetracker.exception

import com.lavish.expensetracker.model.response.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DataAccessException
import org.springframework.web.servlet.NoHandlerFoundException

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = ex.statusCode.toString().split(" ").last().uppercase(),
            message = ex.reason ?: "An error occurred"
        )
        return ResponseEntity(errorResponse, ex.statusCode)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "BAD_REQUEST",
            message = ex.message ?: "Invalid request parameters"
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "Resource not found"
        )
        return ResponseEntity(errorResponse, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "INTERNAL_SERVER_ERROR",
            message = ex.message ?: "An unexpected error occurred. Please try again later."
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(ExpenseCreationException::class)
    fun handleExpenseCreationException(ex: ExpenseCreationException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "EXPENSE_CREATION_FAILED",
            message = ex.message ?: "Failed to create expense. Please try again."
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(ExpenseValidationException::class)
    fun handleExpenseValidationException(ex: ExpenseValidationException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "VALIDATION_FAILED",
            message = ex.message ?: "Please fix the validation errors",
            validationErrors = ex.validationErrors
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(ExpenseNotFoundException::class)
    fun handleExpenseNotFoundException(ex: ExpenseNotFoundException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "EXPENSE_NOT_FOUND",
            message = ex.message ?: "The requested expense could not be found"
        )
        return ResponseEntity(errorResponse, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(ExpenseAccessDeniedException::class)
    fun handleExpenseAccessDeniedException(ex: ExpenseAccessDeniedException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "ACCESS_DENIED",
            message = ex.message ?: "You don't have permission to access this expense"
        )
        return ResponseEntity(errorResponse, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(ex: DataIntegrityViolationException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "DATA_INTEGRITY_VIOLATION",
            message = "Data integrity constraint violation. Please check your input data."
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccessException(ex: DataAccessException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "DATABASE_ERROR",
            message = ex.message ?: "Database operation failed. Please try again later."
        )
        return ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "INVALID_JSON",
            message = ex.message ?: "Invalid JSON format in request body"
        )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(ex: NoHandlerFoundException): ResponseEntity<ApiErrorResponse> {
        val errorResponse = ApiErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "API endpoint not found: ${ex.requestURL}"
        )
        return ResponseEntity(errorResponse, HttpStatus.NOT_FOUND)
    }
}
