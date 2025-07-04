package com.example.expensetracker.security

import com.example.expensetracker.service.JwtService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val objectMapper: ObjectMapper = ObjectMapper()
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val jwt = authHeader.substring(7)
            val userId = jwtService.extractUserId(jwt)

            if (userId != null && SecurityContextHolder.getContext().authentication == null) {
                if (jwtService.isTokenValid(jwt)) {
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
                    val authToken = UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                    logger.debug("Authenticated user: $userId")
                } else {
                    // Token is expired or invalid
                    logger.warn("Invalid or expired token for user: $userId")
                    sendTokenExpiredResponse(response)
                    return
                }
            }
        } catch (e: Exception) {
            logger.error("JWT authentication error: ${e.message}", e)
            sendTokenExpiredResponse(response)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun sendTokenExpiredResponse(response: HttpServletResponse) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse = mapOf(
            "error" to "TOKEN_EXPIRED",
            "message" to "Your session has expired. Please log in again.",
            "timestamp" to System.currentTimeMillis()
        )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
