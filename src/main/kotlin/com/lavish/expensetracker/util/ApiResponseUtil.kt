package com.lavish.expensetracker.util

import com.lavish.expensetracker.model.response.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

object ApiResponseUtil {

    fun badRequest(message: String, validationErrors: List<String>? = null): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                error = "BAD_REQUEST",
                message = message,
                validationErrors = validationErrors
            )
        )
    }

    fun notFound(message: String): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                error = "NOT_FOUND",
                message = message
            )
        )
    }

    fun unauthorized(message: String): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse(
                error = "UNAUTHORIZED",
                message = message
            )
        )
    }

    fun forbidden(message: String): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiErrorResponse(
                error = "FORBIDDEN",
                message = message
            )
        )
    }

    fun conflict(message: String): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                error = "CONFLICT",
                message = message
            )
        )
    }

    fun internalServerError(message: String): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(
                error = "INTERNAL_SERVER_ERROR",
                message = message
            )
        )
    }

    fun unprocessableEntity(message: String, validationErrors: List<String>? = null): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiErrorResponse(
                error = "UNPROCESSABLE_ENTITY",
                message = message,
                validationErrors = validationErrors
            )
        )
    }
}
