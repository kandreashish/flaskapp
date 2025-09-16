# GitHub Copilot Instructions for Expense Tracker

## Project Overview
This is a Kotlin Spring Boot application for expense tracking with the following key characteristics:
- **Language**: Kotlin 2.2.0
- **Framework**: Spring Boot 3.2.4
- **Database**: JPA/Hibernate with H2 (development) and PostgreSQL (production)
- **Authentication**: Firebase Authentication with JWT tokens
- **Architecture**: REST API with layered architecture (Controller → Service → Repository)
- **Build Tool**: Gradle with Kotlin DSL
- **Java Version**: 17

## Project Structure
```
src/main/kotlin/com/lavish/expensetracker/
├── ExpenseTrackerApplication.kt     # Main application entry point
├── config/                          # Configuration classes
├── controller/                      # REST controllers
├── exception/                       # Custom exceptions and handlers
├── health/                          # Health check endpoints
├── model/                           # JPA entities and DTOs
├── repository/                      # JPA repositories
├── security/                        # Security configuration and filters
├── service/                         # Business logic layer
└── util/                           # Utility classes
```

## Key Dependencies
- Spring Boot Starter Web, Data JPA, Security, Actuator
- Firebase Admin SDK for authentication
- JWT (JSON Web Tokens) for session management
- Jackson with Kotlin module for JSON serialization
- H2 Database for development, PostgreSQL for production
- JaCoCo for test coverage

## Coding Standards and Conventions

### 1. Kotlin Style
- Use idiomatic Kotlin features (data classes, extension functions, null safety)
- Prefer immutable data structures when possible
- Use `val` over `var` unless mutability is required
- Follow Kotlin naming conventions (camelCase for properties/functions, PascalCase for classes)

### 2. Spring Boot Patterns
- Use `@RestController` for REST endpoints
- Apply `@Service` for business logic layer
- Use `@Repository` for data access layer
- Implement `@Configuration` classes for custom configurations
- Leverage Spring's dependency injection with constructor injection

### 3. JPA/Database Patterns
- Use JPA entities with proper annotations (`@Entity`, `@Table`, `@Column`)
- Implement custom repository interfaces extending `JpaRepository`
- Use `@Query` annotations for complex queries
- Follow projection interfaces for custom query results (e.g., `TotalAndCountProjection`)
- Use proper parameter binding with `@Param`

### 4. Security Implementation
- Firebase-based authentication with custom security filters
- JWT token validation and user context management
- Role-based access control where applicable
- Secure endpoint patterns with proper authorization

### 5. Exception Handling
- Use custom exception classes in the `exception` package
- Implement global exception handlers with `@ControllerAdvice`
- Return proper HTTP status codes and error responses

### 6. API Design
- RESTful endpoint design with proper HTTP methods
- Use DTOs for request/response objects
- Implement proper validation with Bean Validation annotations
- Return consistent response formats

## Package-Specific Guidelines

### Controllers (`controller/`)
- Keep controllers thin - delegate business logic to services
- Use proper HTTP status codes (200, 201, 400, 401, 403, 404, 500)
- Implement request validation
- Use meaningful endpoint paths following REST conventions

### Services (`service/`)
- Contain all business logic
- Handle transactions with `@Transactional`
- Throw appropriate exceptions for error cases
- Keep methods focused and single-purpose

### Repositories (`repository/`)
- Extend `JpaRepository<Entity, ID>`
- Use method naming conventions for query derivation
- Implement custom queries with `@Query` when needed
- Use projection interfaces for specific data retrieval

### Models (`model/`)
- Use JPA entities with proper relationships
- Implement DTOs for API communication
- Use data classes for immutable objects
- Apply validation annotations where appropriate

### Configuration (`config/`)
- Database configuration and connection settings
- Security configuration classes
- Bean definitions and custom configurations

## Testing Guidelines
- Write unit tests for service layer business logic
- Use integration tests for repository layer
- Mock external dependencies (Firebase, databases)
- Maintain good test coverage (aim for >80%)
- Use meaningful test method names describing the scenario

## Error Handling Patterns
- Use specific exception types for different error scenarios
- Implement proper logging for debugging
- Return user-friendly error messages
- Handle edge cases and validation errors gracefully

## Database Patterns
- Use soft deletes with `deleted` boolean flags
- Implement audit fields (createdAt, updatedAt) where needed
- Use appropriate indexes for query performance
- Handle currency and decimal amounts with `BigDecimal`

## Firebase Integration
- Use Firebase Admin SDK for user authentication
- Validate Firebase tokens in security filters
- Extract user information from Firebase tokens
- Handle Firebase-specific exceptions appropriately

## When Suggesting Code:
1. Always consider the existing project structure and patterns
2. Use appropriate Spring Boot annotations and conventions
3. Follow Kotlin best practices and idiomatic code
4. Ensure proper error handling and logging
5. Consider security implications for user data and authentication
6. Write tests alongside implementation code
7. Use existing utility classes and patterns where applicable
8. Maintain consistency with existing codebase style

## Important Notes:
- The application uses Firebase for authentication, not Spring Security's default mechanisms
- Expenses are associated with users and can be organized by families
- The application supports multiple currencies with currency prefixes
- Date handling uses Long timestamps for date ranges
- Soft deletes are used instead of hard deletes for data integrity
- To start the local server, always use `./start.sh` instead of direct gradle or bootRun commands
- Personal expenses must have null family ID, while family expenses must always have a valid family ID
