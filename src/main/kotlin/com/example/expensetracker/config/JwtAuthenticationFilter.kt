package com.example.expensetracker.config

import com.example.expensetracker.service.JwtService
import com.example.expensetracker.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

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

            if (jwtService.isTokenValid(jwt) && SecurityContextHolder.getContext().authentication == null) {
                val userId = jwtService.extractUserId(jwt)
                val user = userRepository.findById(userId)

                if (user != null) {
                    val authorities = user.roles.map { SimpleGrantedAuthority("ROLE_$it") }
                    val authToken = UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                }
            }
        } catch (e: Exception) {
            logger.error("JWT authentication failed", e)
        }

        filterChain.doFilter(request, response)
    }
}
