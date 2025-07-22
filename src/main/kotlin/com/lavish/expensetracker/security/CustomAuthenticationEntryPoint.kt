package com.lavish.expensetracker.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.lavish.expensetracker.model.response.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {

    private val objectMapper = ObjectMapper()

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        val errorResponse = ApiErrorResponse(
            error = "AUTHENTICATION_REQUIRED",
            message = authException.message ?: "Authentication is required to access this resource. Please provide a valid JWT token."
        )

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val jsonResponse = objectMapper.writeValueAsString(errorResponse)
        response.writer.write(jsonResponse)
    }
}
