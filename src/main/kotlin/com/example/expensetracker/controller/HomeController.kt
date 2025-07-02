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
                    .pagination-info { background: #e8f4fd; padding: 10px; border-radius: 5px; margin: 10px 0; }
                    .sample-response { background: #f1f3f4; padding: 10px; border-radius: 5px; margin: 10px 0; font-family: monospace; font-size: 12px; overflow-x: auto; }
                    code { background: #f1f3f4; padding: 2px 4px; border-radius: 3px; }
                </style>
            </head>
            <body>
                <h1>🏦 Expense Tracker API</h1>
                <p>Welcome to the Expense Tracker API! Your application is running successfully with <strong>pagination support</strong>.</p>
                
                <div class="pagination-info">
                    <strong>📄 Pagination Information:</strong>
                    <ul>
                        <li>All list endpoints support pagination with default values: <code>page=0</code>, <code>size=10</code></li>
                        <li>Page numbers are 0-based (first page = 0)</li>
                        <li>All responses include pagination metadata</li>
                    </ul>
                </div>
                
                <h2>📋 Available Endpoints:</h2>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    <a href="/api/expenses">/api/expenses</a>
                    <div class="description">Get all expenses (paginated) - Default: page=0, size=10</div>
                    <div>Query params: <code>?page=0&size=10</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/{id}
                    <div class="description">Get a specific expense by ID</div>
                </div>
                
                <div class="endpoint">
                    <span class="method post">POST</span> 
                    /api/expenses
                    <div class="description">Create a new expense</div>
                </div>
                
                <div class="endpoint">
                    <span class="method put">PUT</span> 
                    /api/expenses/{id}
                    <div class="description">Update an existing expense</div>
                </div>
                
                <div class="endpoint">
                    <span class="method delete">DELETE</span> 
                    /api/expenses/{id}
                    <div class="description">Delete an expense</div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/category/{category}
                    <div class="description">Get expenses by category (paginated)</div>
                    <div>Query params: <code>?page=0&size=10</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/between-dates
                    <div class="description">Get expenses between date range (paginated)</div>
                    <div>Query params: <code>?startDate=2024-01-01&endDate=2024-12-31&page=0&size=10</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/user/{userId}
                    <div class="description">Get expenses by user ID (paginated)</div>
                    <div>Query params: <code>?page=0&size=10</code></div>
                </div>
                
                <div class="endpoint">
                    <span class="method get">GET</span> 
                    /api/expenses/family/{familyId}
                    <div class="description">Get expenses by family ID (paginated)</div>
                    <div>Query params: <code>?page=0&size=10</code></div>
                </div>
                
                <h2>📊 Sample Paginated Response:</h2>
                <div class="sample-response">
{
  "content": [
    {
      "expense_id": "123e4567-e89b-12d3-a456-426614174000",
      "user_id": "user1",
      "amount": 12575,
      "category": "GROCERIES",
      "description": "Grocery shopping",
      "date": 1719936618000,
      "family_id": "family1",
      "is_date_expense": false,
      "expense_created_on": 1719936618000,
      "created_by": "user1",
      "modified_by": "",
      "last_modified_on": 1719936618000,
      "synced": false
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 5,
  "totalPages": 1,
  "isFirst": true,
  "isLast": true,
  "hasNext": false,
  "hasPrevious": false
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
                <p>Click <a href="/api/expenses">here</a> to view the first page of expenses (sample data should be loaded)</p>
                <p>Or try: <a href="/api/expenses?page=0&size=3">First 3 expenses</a></p>
            </body>
            </html>
        """.trimIndent()
    }
}
