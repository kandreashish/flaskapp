package com.lavish.expensetracker.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.Components
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .openapi("3.0.3")  // Add the required OpenAPI version
            .info(
                Info()
                    .title("Family Expense Tracker API")
                    .version("v1.0")
                    .description("""
                        ## 🏠 Family Expense Tracker API Documentation
                        
                        This API provides comprehensive family and expense management functionality including:
                        
                        ### 👨‍👩‍👧‍👦 Family Management
                        - Create and manage families
                        - Send and manage invitations
                        - Handle join requests
                        - Member management (add/remove)
                        
                        ### 💰 Expense Tracking
                        - Create and manage expenses
                        - Category-based organization
                        - Family expense sharing
                        - Comprehensive reporting
                        
                        ### 🔔 Notification System
                        - Real-time push notifications
                        - In-app notification management
                        - Family activity updates
                        
                        ### 🔐 Security
                        - JWT-based authentication
                        - Role-based access control
                        - Secure API endpoints
                        
                        ---
                        
                        **Note:** All endpoints require authentication unless specified otherwise.
                        Use the "Authorize" button to add your JWT token for testing.
                    """.trimIndent())
                    .contact(
                        Contact()
                            .name("Expense Tracker Support")
                            .email("support@expensetracker.com")
                            .url("https://github.com/your-repo/expense-tracker")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8080")
                        .description("Local Development Server"),
                    Server()
                        .url("https://your-production-url.com")
                        .description("Production Server")
                )
            )
            .addSecurityItem(
                SecurityRequirement().addList("Bearer Authentication")
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        "Bearer Authentication",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Enter your JWT token in the format: Bearer {token}")
                    )
            )
    }
}
