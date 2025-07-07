# 🏦 Expense Tracker API

A modern Spring Boot application for managing personal and family expenses with JWT authentication, pagination, and comprehensive security features.

## ✨ Features

- **🔐 JWT Authentication** - Secure token-based authentication system
- **👥 User Management** - User registration, login, and profile management
- **💰 Expense Management** - Full CRUD operations for expenses
- **📄 Pagination** - Efficient data retrieval with pagination support
- **👨‍👩‍👧‍👦 Family Support** - Group expenses by family units
- **🔍 Advanced Filtering** - Filter by category, date range, user, and family
- **🛡️ Security** - Users can only access their own data
- **📊 Real-time Data** - In-memory storage with instant updates
- **📱 API-First Design** - RESTful API ready for mobile/web frontend integration

## 🚀 Tech Stack

- **Backend**: Spring Boot 3.2.4 with Kotlin
- **Security**: Spring Security with JWT
- **Database**: H2 (in-memory for development)
- **Build Tool**: Gradle with Kotlin DSL
- **Serialization**: Kotlinx Serialization
- **Documentation**: Interactive API documentation

## 🏃‍♂️ Quick Start

### Prerequisites
- Java 17 or higher
- Gradle (included via wrapper)

### Running the Application

1. **Clone and navigate to the project:**
   ```bash
   cd /path/to/flaskapp
   ```

2. **Build the application:**
   ```bash
   ./gradlew build
   ```

3. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

4. **Access the application:**
   - API Documentation: http://localhost:3000
   - H2 Database Console: http://localhost:3000/h2-console

### 🧪 Test Credentials
The application automatically creates test users on startup:
- **User 1**: `john@example.com` / `password123`
- **User 2**: `jane@example.com` / `password123`

## 📚 API Documentation

### 🔑 Authentication Endpoints

#### Register New User
```http
POST /api/auth/signup
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123",
  "familyId": "" // Optional, auto-generated if empty
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "user-id",
    "name": "John Doe", 
    "email": "john@example.com",
    "familyId": "family-id"
  },
  "expiresIn": 86400000
}
```

#### Get Current User
```http
GET /api/auth/me
Authorization: Bearer {jwt_token}
```

### 💰 Expense Endpoints (All require JWT authentication)

#### Get User's Expenses (Paginated)
```http
GET /api/expenses?page=0&size=10
Authorization: Bearer {jwt_token}
```

#### Create Expense
```http
POST /api/expenses
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "amount": 2500,
  "category": "GROCERIES",
  "description": "Weekly grocery shopping",
  "date": 1719936618000
}
```

#### Update Expense
```http
PUT /api/expenses/{expense_id}
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "amount": 3000,
  "category": "GROCERIES", 
  "description": "Updated grocery shopping"
}
```

#### Delete Expense
```http
DELETE /api/expenses/{expense_id}
Authorization: Bearer {jwt_token}
```

#### Filter Expenses
```http
# By Category
GET /api/expenses/category/GROCERIES?page=0&size=10
Authorization: Bearer {jwt_token}

# By Date Range  
GET /api/expenses/between-dates?startDate=2024-01-01&endDate=2024-12-31&page=0&size=10
Authorization: Bearer {jwt_token}

# Family Expenses
GET /api/expenses/family?page=0&size=10
Authorization: Bearer {jwt_token}
```

## 🔧 Configuration

### Application Properties
```properties
# Server Configuration
server.port=3000

# Database Configuration
spring.datasource.url=jdbc:h2:mem:expensedb
spring.datasource.username=sa
spring.datasource.password=

# JWT Configuration
jwt.secret=your-secret-key-here
jwt.expiration=86400000

# H2 Console
spring.h2.console.enabled=true
```

### Security Configuration
- All expense endpoints require JWT authentication
- Users can only access their own expenses
- JWT tokens expire in 24 hours
- Passwords are encrypted using BCrypt

## 📊 Data Models

### ExpenseDto
```kotlin
{
  "expense_id": "uuid",
  "user_id": "uuid", 
  "amount": 2500, // Amount in cents
  "category": "GROCERIES",
  "description": "Weekly shopping",
  "date": 1719936618000, // Epoch timestamp
  "family_id": "uuid",
  "is_date_expense": false,
  "expense_created_on": 1719936618000,
  "created_by": "user-id",
  "modified_by": "user-id", 
  "last_modified_on": 1719936618000,
  "synced": false
}
```

### ExpenseUser
```kotlin
{
  "id": "uuid",
  "name": "John Doe",
  "email": "john@example.com", 
  "profile_url": "https://...",
  "family_id": "uuid",
  "updated_at": 1719936618000,
  "roles": ["USER"]
}
```

## 🏗️ Project Structure

```
src/main/kotlin/com/example/expensetracker/
├── ExpenseTrackerApplication.kt     # Main application class
├── config/                          # Configuration classes
│   ├── SecurityConfig.kt           # Spring Security configuration
│   ├── JwtAuthenticationFilter.kt  # JWT filter
│   └── UserDataInitializer.kt      # Test data initialization
├── controller/                      # REST controllers
│   ├── AuthController.kt           # Authentication endpoints
│   ├── ExpenseController.kt        # Expense CRUD endpoints
│   └── HomeController.kt           # API documentation page
├── model/                          # Data models
│   ├── ExpenseDto.kt              # Expense data model
│   ├── ExpenseUser.kt             # User data model
│   ├── PagedResponse.kt           # Pagination wrapper
│   └── auth/                      # Authentication models
├── repository/                     # Data access layer
│   ├── ExpenseRepository.kt       # Expense repository interface
│   ├── ExpenseRepositoryImpl.kt   # In-memory implementation
│   └── UserRepository.kt          # User repository
├── service/                       # Business logic
│   ├── AuthService.kt            # Authentication service
│   ├── ExpenseService.kt         # Expense business logic
│   └── JwtService.kt             # JWT token management
└── util/                         # Utility classes
    └── AuthUtil.kt               # Authentication utilities
```

## 🔒 Security Features

- **JWT Token Authentication**: Stateless authentication with 24-hour expiration
- **Password Encryption**: BCrypt hashing for secure password storage
- **User Isolation**: Users can only access their own expenses
- **Protected Endpoints**: All expense APIs require valid JWT tokens
- **Auto-Assignment**: Expenses automatically assigned to authenticated user
- **Role-Based Access**: Support for user roles and permissions

# Expense Tracker Application

A Spring Boot expense tracking application built with Kotlin.

## Getting Started

### Prerequisites
- Docker and Docker Compose
- Java 17+ (for local development)

### Running with Docker Compose

The application uses Docker Compose for easy deployment. When you start the application using Docker Compose, it will automatically execute the `start.sh` script to initialize and start the application.

```bash
# Start the application
docker-compose up -d

# View logs
docker-compose logs -f expense-tracker

# Stop the application
docker-compose down
```

### Docker Configuration

The Docker Compose configuration includes:
- **Port**: Application runs on port 3000
- **Environment**: Uses production profile (`SPRING_PROFILES_ACTIVE=prod`)
- **Volumes**: H2 database data is persisted in `./data/h2`
- **Health Check**: Monitors application health via `/actuator/health` endpoint
- **Restart Policy**: Automatically restarts unless stopped manually
- **Startup Command**: Uses `./start.sh` script for initialization

### Application Features

- Expense tracking and management
- REST API endpoints
- H2 database for data persistence
- Spring Boot Actuator for monitoring
- Health check endpoints

### Development

For local development without Docker:
```bash
./gradlew bootRun
```

### Health Check

The application health can be monitored at:
```
http://localhost:3000/actuator/health
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For support and questions:
- Check the API documentation at http://localhost:3000
- Review the H2 database at http://localhost:3000/h2-console
- Use the test credentials provided above

---

**Built with ❤️ using Spring Boot and Kotlin**
