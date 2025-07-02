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
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    h1 { color: #333; }
                    .endpoint { background: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 5px; }
                    .method { font-weight: bold; color: #007bff; }
                    a { color: #007bff; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
                <h1>🏦 Expense Tracker API</h1>
                <p>Welcome to the Expense Tracker API! Your application is running successfully.</p>
                
                <h2>Available Endpoints:</h2>
                
                <div class="endpoint">
                    <span class="method">GET</span> 
                    <a href="/api/expenses">/api/expenses</a> - Get all expenses
                </div>
                
                <div class="endpoint">
                    <span class="method">GET</span> 
                    /api/expenses/{id} - Get expense by ID
                </div>
                
                <div class="endpoint">
                    <span class="method">POST</span> 
                    /api/expenses - Create new expense
                </div>
                
                <div class="endpoint">
                    <span class="method">PUT</span> 
                    /api/expenses/{id} - Update expense
                </div>
                
                <div class="endpoint">
                    <span class="method">DELETE</span> 
                    /api/expenses/{id} - Delete expense
                </div>
                
                <div class="endpoint">
                    <span class="method">GET</span> 
                    /api/expenses/category/{category} - Get expenses by category
                </div>
                
                <h2>Database Console:</h2>
                <p><a href="/h2-console" target="_blank">🗄️ H2 Database Console</a></p>
                <p><strong>Connection Details:</strong></p>
                <ul>
                    <li>JDBC URL: jdbc:h2:mem:expensedb</li>
                    <li>Username: sa</li>
                    <li>Password: password</li>
                </ul>
                
                <h2>Quick Test:</h2>
                <p>Click <a href="/api/expenses">here</a> to view all expenses (sample data should be loaded)</p>
            </body>
            </html>
        """.trimIndent()
    }
}
