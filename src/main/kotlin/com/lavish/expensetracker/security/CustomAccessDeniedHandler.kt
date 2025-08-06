package com.lavish.expensetracker.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.lavish.expensetracker.model.response.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class CustomAccessDeniedHandler : AccessDeniedHandler {

    private val objectMapper = ObjectMapper()

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        val errorResponse = ApiErrorResponse(
            error = "ACCESS_DENIED",
            message = accessDeniedException.message ?: "Access denied. You don't have permission to access this resource."
        )

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val jsonResponse = objectMapper.writeValueAsString(errorResponse)
        response.writer.write(jsonResponse)
    }
}
