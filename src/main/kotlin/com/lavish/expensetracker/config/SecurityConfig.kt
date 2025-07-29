package com.lavish.expensetracker.config

import com.lavish.expensetracker.security.JwtAuthFilter
import com.lavish.expensetracker.security.CustomAuthenticationEntryPoint
import com.lavish.expensetracker.security.CustomAccessDeniedHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val customAccessDeniedHandler: CustomAccessDeniedHandler
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors(Customizer.withDefaults())
            .csrf { it.disable() }  // Disable CSRF for REST API
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints that don't require authentication
                    .requestMatchers("/").permitAll() // Home page
                    .requestMatchers("/api/auth/**").permitAll() // All auth endpoints
                    .requestMatchers("/api/build/**").permitAll() // Build info endpoints
                    .requestMatchers("/api/files/**").permitAll() // File serving endpoints (profile pictures)
                    .requestMatchers("/h2-console/**").permitAll() // H2 console
                    .requestMatchers("/error").permitAll() // Error page
                    .requestMatchers("/actuator/health").permitAll() // Health check
                    .requestMatchers("/.well-known/**").permitAll() // SSL certificate verification

                    // Swagger 2.0 endpoints (replacing OpenAPI 3.0)
                    .requestMatchers("/v3/api-docs/**").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/swagger-ui.html").permitAll()
                    .requestMatchers("/swagger-resources/**").permitAll()
                    .requestMatchers("/webjars/**").permitAll()
                    .requestMatchers("/configuration/**").permitAll()

                    // All other requests require authentication
                    .anyRequest().authenticated()
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .headers { headers ->
                headers.frameOptions(Customizer.withDefaults()).disable()
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("*")  //Todo In production, restrict to your domain
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
