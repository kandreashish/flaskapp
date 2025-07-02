package com.example.expensetracker.security

import com.example.expensetracker.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

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
                }
            }
        } catch (e: Exception) {
            logger.error("JWT authentication error: ${e.message}", e)
            // Don't throw the exception to avoid breaking the filter chain
        }

        filterChain.doFilter(request, response)
    }
}
