package com.lavish.expensetracker.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class HomeController {

    @GetMapping("/")
    @ResponseBody
    fun home(): String {
        return """
<!DOCTYPE html>
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
        code { background: #f1f3f4; padding: 2px 4px; border-radius: 3px; }
        .auth-info { background: #fff3cd; padding: 15px; border-radius: 5px; margin: 10px 0; border-left: 4px solid #ffc107; }
        .sample-response { background: #f1f3f4; padding: 10px; border-radius: 5px; margin: 10px 0; font-family: monospace; font-size: 12px; overflow-x: auto; }
    </style>
</head>
<body>
    <h1>🏦 Expense Tracker API</h1>
    <p>Welcome to the Expense Tracker API! Your application is running successfully.</p>
    
    <h2>🔧 Build Info Endpoints (No Authentication Required):</h2>
    
    <div class="endpoint">
        <span class="method get">GET</span> 
        /api/build/info
        <div>Get detailed information about build JAR files</div>
    </div>
    
    <div class="endpoint">
        <span class="method get">GET</span> 
        /api/build/manifest
        <div>Get detailed manifest attributes for all JAR files</div>
    </div>
    
    <h2>🔑 Authentication Endpoints:</h2>
    
    <div class="endpoint">
        <span class="method post">POST</span> 
        /api/auth/login
        <div>Login and get JWT token</div>
        <div>Body: <code>{"email": "john@example.com", "password": "password123"}</code></div>
    </div>
    
    <div class="endpoint">
        <span class="method post">POST</span> 
        /api/auth/signup
        <div>Create a new user account</div>
    </div>
    
    <h2>📋 Expense Endpoints (Authentication Required):</h2>
    
    <div class="endpoint">
        <span class="method get">GET</span> 
        /api/expenses
        <div>Get current user's expenses (paginated)</div>
        <div>Headers: <code>Authorization: Bearer {jwt_token}</code></div>
    </div>
    
    <h2>📦 Sample Build Info Response:</h2>
    <div class="sample-response">
{
  "buildDirectory": "/path/to/build/libs",
  "jarFiles": [
    {
      "fileName": "expense-tracker-0.0.1-SNAPSHOT.jar",
      "fileSize": 112206173,
      "fileSizeFormatted": "107.0 MB",
      "manifestInfo": {
        "Implementation-Title": "expense-tracker",
        "Implementation-Version": "0.0.1-SNAPSHOT"
      }
    }
  ],
  "totalJars": 2
}
    </div>
    
    <h2>🗄️ Database Console:</h2>
    <p><a href="/h2-console" target="_blank">H2 Database Console</a></p>
    
    <h2>🚀 Quick Test:</h2>
    <p>1. Test build info: <code>GET /api/build/info</code></p>
    <p>2. Login: <code>POST /api/auth/login</code></p>
    <p>3. Access expenses: <code>GET /api/expenses</code> with Authorization header</p>
</body>
</html>
        """.trimIndent()
    }
}
