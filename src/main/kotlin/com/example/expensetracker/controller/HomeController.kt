package com.example.expensetracker.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class HomeController {

    @GetMapping("/")
    @ResponseBody
    fun home(): String {
        return """
            <html>
            <head>
                <title>Expense Tracker API</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; line-height: 1.6; }
                    h1 { color: #333; }
                    h2 { color: #555; margin-top: 30px; }
                    .endpoint { background: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid #007bff; }
                    .method { font-weight: bold; color: #007bff; padding: 4px 8px; background: #e3f2fd; border-radius: 4px; margin-right: 8px; }
                    .method.get { color: #28a745; background: #d4edda; }
                    .method.post { color: #fd7e14; background: #fff3cd; }
                    .method.put { color: #6f42c1; background: #e2e3f0; }
                    .method.delete { color: #dc3545; background: #f8d7da; }
                    a { color: #007bff; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    .description { color: #666; margin-top: 5px; font-style: italic; }
                    .auth-info { background: #fff3cd; padding: 15px; border-radius: 5px; margin: 10px 0; border-left: 4px solid #ffc107; }
                    .pagination-info { background: #e8f4fd; padding: 10px; border-radius: 5px; margin: 10px 0; }
                    .sample-response { background: #f1f3f4; padding: 10px; border-radius: 5px; margin: 10px 0; font-family: monospace; font-size: 12px; overflow-x: auto; }
                    code { background: #f1f3f4; padding: 2px 4px; border-radius: 3px; }
                    .test-credentials { background: #d1ecf1; padding: 10px; border-radius: 5px; margin: 10px 0; }
                </style>
            </head>
            <body>
                <h1>🏦 Expense Tracker API</h1>
                <p>Welcome to the Expense Tracker API! Your application is running successfully with <strong>JWT Authentication</strong> and <strong>pagination support</strong>.</p>
                
                <div class="auth-info">
                    <strong>🔐 Authentication Required:</strong>
                    <ul>
                        <li>All expense endpoints require JWT authentication</li>
                        <li>Include JWT token in Authorization header: <code>Bearer {token}</code></li>
                        <li>Users can only access their own expenses</li>
                    </ul>
                </div>
                
                <div class="test-credentials">
                    <strong>🧪 Test User Credentials:</strong>
                    <ul>
                        <li>Email: <code>john@example.com</code> | Password: <code>password123</code></li>
                        <li>Email: <code>jane@example.com</code> | Password: <code>password123</code></li>
                    </ul>
                </div>
                
                <h2>🔑 Authentication Endpoints:</h2>
                
                <div class="endpoint">
                    <span class="method post">POST</span> 
                    /api/auth/signup
                    <div class="description">Create a new user account</div>
                    <div>Body: <code>{"name": "John Doe", "email": "john@example.com", "password": "password123"}</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method post">POST</span> 
                    /api/auth/login
                    <div class="description">Login and get JWT token</div>
                    <div>Body: <code>{"email": "john@example.com", "password": "password123"}</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/auth/me
                    <div class="description">Get current user information (requires JWT token)</div>
                </div>
                
                <div class="pagination-info">
                    <strong>📄 Pagination Information:</strong>
                    <ul>
                        <li>All list endpoints support pagination with default values: <code>page=0</code>, <code>size=10</code></li>
                        <li>Page numbers are 0-based (first page = 0)</li>
                        <li>All responses include pagination metadata</li>
                    </ul>
                </div>
                
                <h2>📋 Expense Endpoints (Authentication Required):</h2>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses
                    <div class="description">Get current user's expenses (paginated) - Default: page=0, size=10</div>
                    <div>Headers: <code>Authorization: Bearer {jwt_token}</code></div>
                    <div>Query params: <code>?page=0&size=10</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/{id}
                    <div class="description">Get a specific expense by ID (user's own expenses only)</div>
                </div>
                
                <div class="endpoint">
                    <span class="method post">POST</span> 
                    /api/expenses
                    <div class="description">Create a new expense (automatically assigned to current user)</div>
                </div>
                
                <div class="endpoint">
                    <span class="method put">PUT</span> 
                    /api/expenses/{id}
                    <div class="description">Update an existing expense (user's own expenses only)</div>
                </div>
                
                <div class="endpoint">
                    <span class="method delete">DELETE</span> 
                    /api/expenses/{id}
                    <div class="description">Delete an expense (user's own expenses only)</div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/category/{category}
                    <div class="description">Get current user's expenses by category (paginated)</div>
                    <div>Query params: <code>?page=0&size=10</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/between-dates
                    <div class="description">Get current user's expenses between date range (paginated)</div>
                    <div>Query params: <code>?startDate=2024-01-01&endDate=2024-12-31&page=0&size=10</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/family
                    <div class="description">Get current user's family expenses (paginated)</div>
                    <div>Query params: <code>?page=0&size=10</code></div>
                </div>
                
                <h2>📊 Sample Authentication Response:</h2>
                <div class="sample-response">
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIxMjM...",
  "user": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "name": "John Doe",
    "email": "john@example.com",
    "familyId": "family123"
  },
  "expiresIn": 86400000
}
                </div>
                
                <h2>🗄️ Database Console:</h2>
                <p><a href="/h2-console" target="_blank">H2 Database Console</a></p>
                <p><strong>Connection Details:</strong></p>
                <ul>
                    <li>JDBC URL: <code>jdbc:h2:mem:expensedb</code></li>
                    <li>Username: <code>sa</code></li>
                    <li>Password: <em>(leave empty)</em></li>
                </ul>
                
                <h2>🚀 Quick Test:</h2>
                <p>1. First login to get JWT token: <code>POST /api/auth/login</code></p>
                <p>2. Use the token to access expenses: <code>GET /api/expenses</code> with <code>Authorization: Bearer {token}</code></p>
            </body>
            </html>
        """.trimIndent()
    }
}
