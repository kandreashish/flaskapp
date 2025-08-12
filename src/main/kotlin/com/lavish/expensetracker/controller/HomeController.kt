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
    <title>Expense Tracker API Documentation</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; line-height: 1.6; }
        h1 { color: #333; }
        h2 { color: #555; margin-top: 30px; }
        .endpoint { background: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid #007bff; }
        .method { font-weight: bold; padding: 4px 8px; border-radius: 4px; margin-right: 8px; display: inline-block; min-width: 60px; text-align: center; }
        .method.get { color: #28a745; background: #d4edda; }
        .method.post { color: #fd7e14; background: #fff3cd; }
        .method.put { color: #0056b3; background: #cce5ff; }
        .method.delete { color: #dc3545; background: #f8d7da; }
        code { background: #f1f3f4; padding: 2px 4px; border-radius: 3px; }
        .auth-info { background: #fff3cd; padding: 15px; border-radius: 5px; margin: 10px 0; border-left: 4px solid #ffc107; }
        .sample-response, .sample-request { background: #f1f3f4; padding: 10px; border-radius: 5px; margin: 10px 0; font-family: monospace; font-size: 12px; overflow-x: auto; }
        .code-label { font-weight: bold; margin-top: 10px; color: #555; }
        .section { border-bottom: 1px solid #eee; padding-bottom: 20px; margin-bottom: 20px; }
        .scrollable { max-height: 300px; overflow-y: auto; }
    </style>
</head>
<body>
    <h1>🏦 Expense Tracker API Documentation</h1>
    <p>Welcome to the Expense Tracker API documentation! This page lists all available endpoints with example requests and responses.</p>
    
    <div class="auth-info">
        <h3>🔐 Authentication</h3>
        <p>Most endpoints require authentication with a JWT token. Include the token in the Authorization header:</p>
        <code>Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...</code>
    </div>

    <div class="section">
        <h2>🔑 Authentication Endpoints</h2>
        
        <div class="endpoint">
            <span class="method post">POST</span> 
            /api/auth/login
            <div>Login with Firebase and get JWT token</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "idToken": "firebase-id-token-here"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "success": true,
  "message": "Login successful",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "user123",
    "email": "user@example.com",
    "displayName": "John Doe",
    "profilePic": "https://storage.googleapis.com/profile-pics/user123.jpg",
    "currencyPreference": "$"
  }
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method get">GET</span> 
            /api/auth/me
            <div>Get current user profile information</div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "id": "user123",
  "email": "user@example.com",
  "displayName": "John Doe",
  "profilePic": "https://storage.googleapis.com/profile-pics/user123.jpg",
  "currencyPreference": "$",
  "createdAt": "2025-06-12T15:23:11.123Z",
  "lastLogin": "2025-08-12T09:45:22.543Z"
}
            </div>
        </div>
    </div>

    <div class="section">
        <h2>📋 Expense Endpoints</h2>
        
        <div class="endpoint">
            <span class="method get">GET</span> 
            /api/expenses
            <div>Get current user's expenses (paginated)</div>
            <div class="code-label">Query Parameters:</div>
            <code>page</code> - Page number (default: 0)<br>
            <code>size</code> - Page size (default: 10, max: 500)<br>
            <code>sortBy</code> - Sort field (default: "date")<br>
            <code>isAsc</code> - Sort direction (default: false)
            <div class="code-label">Response:</div>
            <div class="sample-response scrollable">
{
  "content": [
    {
      "expenseId": "exp123",
      "amount": 42.99,
      "category": "FOOD",
      "description": "Grocery shopping",
      "date": "2025-08-10",
      "userId": "user123",
      "expenseCreatedOn": "2025-08-10T14:23:45.123Z",
      "lastModifiedOn": "2025-08-10T14:23:45.123Z",
      "familyShared": false,
      "attachments": []
    },
    {
      "expenseId": "exp456",
      "amount": 9.99,
      "category": "ENTERTAINMENT",
      "description": "Movie streaming subscription",
      "date": "2025-08-09",
      "userId": "user123",
      "expenseCreatedOn": "2025-08-09T10:15:22.456Z",
      "lastModifiedOn": "2025-08-09T10:15:22.456Z",
      "familyShared": true,
      "attachments": []
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    }
  },
  "totalElements": 42,
  "totalPages": 5,
  "last": false,
  "size": 10,
  "number": 0,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "first": true,
  "numberOfElements": 10,
  "empty": false
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method post">POST</span> 
            /api/expenses
            <div>Create a new expense</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "amount": 42.99,
  "category": "FOOD",
  "description": "Grocery shopping",
  "date": "2025-08-12"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "expenseId": "exp789",
  "amount": 42.99,
  "category": "FOOD",
  "description": "Grocery shopping",
  "date": "2025-08-12",
  "userId": "user123",
  "expenseCreatedOn": "2025-08-12T15:45:22.123Z",
  "lastModifiedOn": "2025-08-12T15:45:22.123Z",
  "familyShared": false,
  "attachments": []
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method get">GET</span> 
            /api/expenses/{expenseId}
            <div>Get a specific expense by ID</div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "expenseId": "exp123",
  "amount": 42.99,
  "category": "FOOD",
  "description": "Grocery shopping",
  "date": "2025-08-10",
  "userId": "user123",
  "expenseCreatedOn": "2025-08-10T14:23:45.123Z",
  "lastModifiedOn": "2025-08-10T14:23:45.123Z",
  "familyShared": false,
  "attachments": []
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method put">PUT</span> 
            /api/expenses/{expenseId}
            <div>Update an existing expense</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "amount": 45.99,
  "category": "FOOD",
  "description": "Grocery shopping and snacks",
  "date": "2025-08-10"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "expenseId": "exp123",
  "amount": 45.99,
  "category": "FOOD",
  "description": "Grocery shopping and snacks",
  "date": "2025-08-10",
  "userId": "user123",
  "expenseCreatedOn": "2025-08-10T14:23:45.123Z",
  "lastModifiedOn": "2025-08-12T16:05:12.456Z",
  "familyShared": false,
  "attachments": []
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method delete">DELETE</span> 
            /api/expenses/{expenseId}
            <div>Delete an expense</div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "message": "Expense deleted successfully"
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method get">GET</span> 
            /api/expenses/sync
            <div>Sync expenses (for mobile app)</div>
            <div class="code-label">Query Parameters:</div>
            <code>lastSyncTime</code> - Timestamp of last sync (milliseconds since epoch)<br>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "expenses": [
    {
      "expenseId": "exp123",
      "amount": 45.99,
      "category": "FOOD",
      "description": "Grocery shopping and snacks",
      "date": "2025-08-10",
      "userId": "user123",
      "expenseCreatedOn": "2025-08-10T14:23:45.123Z",
      "lastModifiedOn": "2025-08-12T16:05:12.456Z",
      "familyShared": false,
      "attachments": []
    }
  ],
  "deletedExpenseIds": ["exp456"],
  "currentServerTime": 1691658789123
}
            </div>
        </div>
    </div>
    
    <div class="section">
        <h2>👪 Family Management Endpoints</h2>
        
        <div class="endpoint">
            <span class="method post">POST</span> 
            /api/family/create
            <div>Create a new family</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "familyName": "Doe Family"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "message": "Family created successfully",
  "family": {
    "id": "family123",
    "name": "Doe Family",
    "createdByUserId": "user123",
    "aliasName": "DOE456",
    "createdOn": "2025-08-12T16:28:33.789Z",
    "members": ["user123"],
    "memberCount": 1
  }
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method get">GET</span> 
            /api/family
            <div>Get current user's family</div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "id": "family123",
  "name": "Doe Family",
  "createdByUserId": "user123",
  "aliasName": "DOE456",
  "createdOn": "2025-08-12T16:28:33.789Z",
  "members": [
    {
      "id": "user123",
      "displayName": "John Doe",
      "email": "john@example.com",
      "profilePic": "https://storage.googleapis.com/profile-pics/user123.jpg"
    }
  ],
  "pendingMembers": [
    {
      "email": "jane@example.com",
      "invitedOn": "2025-08-12T16:35:12.456Z"
    }
  ],
  "joinRequests": []
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method post">POST</span> 
            /api/family/invite
            <div>Invite a user to family</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "invitedMemberEmail": "jane@example.com"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "message": "Invitation sent successfully",
  "family": {
    "id": "family123",
    "name": "Doe Family",
    "pendingMembers": [
      {
        "email": "jane@example.com",
        "invitedOn": "2025-08-12T16:35:12.456Z"
      }
    ]
  }
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method post">POST</span> 
            /api/family/join
            <div>Join a family using alias</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "aliasName": "DOE456"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "message": "Join request sent successfully",
  "family": {
    "id": "family123",
    "name": "Doe Family"
  }
}
            </div>
        </div>
    </div>
    
    <div class="section">
        <h2>📱 User Profile Endpoints</h2>
        
        <div class="endpoint">
            <span class="method put">PUT</span> 
            /api/users/profile
            <div>Update user profile</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "displayName": "John Doe Jr.",
  "currencyPreference": "€"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "id": "user123",
  "email": "user@example.com",
  "displayName": "John Doe Jr.",
  "profilePic": "https://storage.googleapis.com/profile-pics/user123.jpg",
  "currencyPreference": "€"
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method post">POST</span> 
            /api/users/profile-pic
            <div>Upload a profile picture (form-data)</div>
            <div class="code-label">Request:</div>
            <div>Form data with field name "file" containing the image</div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "message": "Profile picture uploaded successfully",
  "profilePicUrl": "https://storage.googleapis.com/profile-pics/user123.jpg"
}
            </div>
        </div>
        
        <div class="endpoint">
            <span class="method post">POST</span> 
            /api/users/device
            <div>Register a device for push notifications</div>
            <div class="code-label">Request:</div>
            <div class="sample-request">
{
  "deviceToken": "firebase-fcm-token-here",
  "deviceType": "ANDROID",
  "deviceName": "Google Pixel 7"
}
            </div>
            <div class="code-label">Response:</div>
            <div class="sample-response">
{
  "message": "Device registered successfully",
  "deviceId": "device123"
}
            </div>
        </div>
    </div>
    
    <div class="section">
        <h2>🔔 Notification Endpoints</h2>
        <div class="endpoint">
            <span class="method get">GET</span> 
            /api/notifications
            <div>Get user notifications</div>
            <div><b>Authentication:</b> Required</div>
            <div><b>Status Codes:</b> 200 OK, 401 Unauthorized</div>
            <div class="code-label">Query Parameters:</div>
            <ul>
                <li><code>page</code> (integer, optional) - Page number (default: 0)</li>
                <li><code>size</code> (integer, optional) - Page size (default: 20)</li>
            </ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">
{
  "content": [
    {
      "id": 123,
      "type": "FAMILY_INVITATION",
      "title": "Family Invitation",
      "body": "Jane Doe invited you to join Doe Family",
      "data": {
        "familyId": "family456",
        "familyName": "Doe Family",
        "inviterId": "user456"
      },
      "read": false,
      "createdAt": "2025-08-12T10:23:45.123Z"
    },
    {
      "id": 122,
      "type": "EXPENSE_SHARED",
      "title": "New Shared Expense",
      "body": "Jane Doe added an expense: Dinner $75.50",
      "data": {
        "expenseId": "exp789",
        "amount": 75.50,
        "category": "FOOD"
      },
      "read": true,
      "createdAt": "2025-08-11T18:15:33.456Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 5,
  "totalPages": 1,
  "last": true,
  "first": true,
  "empty": false
}
            </div>
            <div class="code-label">Error Response (401):</div>
            <div class="sample-response">
{
  "timestamp": "2025-08-12T10:23:45.123Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/notifications"
}
            </div>
        </div>
        <div class="endpoint">
            <span class="method put">PUT</span> 
            /api/notifications/{notificationId}/read
            <div>Mark notification as read</div>
            <div><b>Authentication:</b> Required</div>
            <div><b>Status Codes:</b> 200 OK, 404 Not Found, 401 Unauthorized</div>
            <div class="code-label">Path Parameters:</div>
            <ul>
                <li><code>notificationId</code> (long, required) - Notification ID</li>
            </ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">
{
  "message": "Notification marked as read"
}
            </div>
            <div class="code-label">Error Response (404):</div>
            <div class="sample-response">
{
  "timestamp": "2025-08-12T10:23:45.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Notification not found",
  "path": "/api/notifications/999/read"
}
            </div>
        </div>
        <div class="endpoint">
            <span class="method put">PUT</span> 
            /api/notifications/read-all
            <div>Mark all notifications as read</div>
            <div><b>Authentication:</b> Required</div>
            <div><b>Status Codes:</b> 200 OK, 401 Unauthorized</div>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">
{
  "message": "All notifications marked as read",
  "count": 5
}
            </div>
        </div>
    </div>
    
    <div class="section">
        <h2>🔔 Notification Endpoints (Detailed)</h2>
        <div style="background:#e9ecef;padding:10px;border-radius:6px;margin-bottom:10px;">
            <b>Legend:</b> <br>
            <span class="method get">GET</span>, <span class="method post">POST</span>, <span class="method put">PUT</span>, <span class="method delete">DELETE</span> = HTTP methods<br>
            <b>Authentication:</b> Required for all endpoints.<br>
            <b>Status Codes:</b> <span style="color:#28a745">200 OK</span>, <span style="color:#dc3545">4xx/5xx Error</span><br>
            <b>Path Params:</b> <code>{param}</code> = required, <code>[param]</code> = optional
        </div>
        <div class="endpoint">
            <span class="method get">GET</span> <b>/api/notifications</b>
            <div>Get paginated notifications for the current user. Supports cursor-based pagination with <code>lastNotificationId</code>.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 401 Unauthorized, 500 Error</li>
            </ul>
            <div class="code-label">Query Parameters:</div>
            <ul>
                <li><code>size</code> (integer, optional) - Page size (default: 10, max: 100)</li>
                <li><code>lastNotificationId</code> (long, optional) - For cursor-based pagination</li>
            </ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "content": [ { /* Notification object */ } ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "isFirst": true,
  "isLast": false,
  "hasNext": true,
  "hasPrevious": false,
  "lastExpenseId": "123"
}</div>
        </div>
        <div class="endpoint">
            <span class="method get">GET</span> <b>/api/notifications/{id}</b>
            <div>Get a notification by its ID.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 404 Not Found, 401 Unauthorized</li>
            </ul>
            <div class="code-label">Path Parameters:</div>
            <ul><li><code>id</code> (long, required) - Notification ID</li></ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "Notification retrieved successfully",
  "status": "success"
}</div>
        </div>
        <div class="endpoint">
            <span class="method post">POST</span> <b>/api/notifications</b>
            <div>Create a new notification.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 400 Bad Request, 500 Error</li>
            </ul>
            <div class="code-label">Request:</div>
            <div class="sample-request">{
  "title": "Family Invitation",
  "message": "You have been invited to join...",
  "type": "JOIN_FAMILY_INVITATION",
  "familyId": "family123",
  "senderId": "user123",
  "receiverId": "user456"
}</div>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "Notification created successfully",
  "status": "success"
}</div>
        </div>
        <div class="endpoint">
            <span class="method put">PUT</span> <b>/api/notifications/{id}</b>
            <div>Update a notification by its ID.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 404 Not Found, 400 Bad Request, 500 Error</li>
            </ul>
            <div class="code-label">Path Parameters:</div>
            <ul><li><code>id</code> (long, required) - Notification ID</li></ul>
            <div class="code-label">Request:</div>
            <div class="sample-request">{
  "title": "Updated Title",
  "message": "Updated message...",
  "type": "GENERAL",
  ...
}</div>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "Notification updated successfully",
  "status": "success",
  "data": { /* Updated Notification object */ }
}</div>
        </div>
        <div class="endpoint">
            <span class="method delete">DELETE</span> <b>/api/notifications/{id}</b>
            <div>Delete a notification by its ID.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 404 Not Found, 500 Error</li>
            </ul>
            <div class="code-label">Path Parameters:</div>
            <ul><li><code>id</code> (long, required) - Notification ID</li></ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "Notification deleted successfully",
  "status": "success"
}</div>
        </div>
        <div class="endpoint">
            <span class="method put">PUT</span> <b>/api/notifications/{id}/mark-read</b>
            <div>Mark a notification as read by its ID.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 404 Not Found, 500 Error</li>
            </ul>
            <div class="code-label">Path Parameters:</div>
            <ul><li><code>id</code> (long, required) - Notification ID</li></ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "Notification marked as read successfully",
  "status": "success",
  "data": { /* Notification object */ }
}</div>
        </div>
        <div class="endpoint">
            <span class="method put">PUT</span> <b>/api/notifications/mark-all-read</b>
            <div>Mark all notifications as read for the current user.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 500 Error</li>
            </ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "10 notifications marked as read",
  "status": "success"
}</div>
        </div>
        <div class="endpoint">
            <span class="method get">GET</span> <b>/api/notifications/unread</b>
            <div>Get all unread notifications for the current user.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 500 Error</li>
            </ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "Found 3 unread notifications",
  "status": "success"
}</div>
        </div>
        <div class="endpoint">
            <span class="method get">GET</span> <b>/api/notifications/{id}/details</b>
            <div>Get detailed information for a notification by its ID.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 403 Forbidden, 404 Not Found, 500 Error</li>
            </ul>
            <div class="code-label">Path Parameters:</div>
            <ul><li><code>id</code> (long, required) - Notification ID</li></ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "message": "Notification details retrieved successfully",
  "status": "success",
  "data": {
    "id": 123,
    "title": "Family Invitation",
    "message": "You have been invited...",
    "customMessage": "📨 Family invitation: You've been invited...",
    "timestamp": 1691658789123,
    "isRead": false,
    "familyId": "family123",
    "familyAlias": "DOE456",
    "senderName": "John Doe",
    "senderId": "user123",
    "receiverId": "user456",
    "actionable": true,
    "type": "JOIN_FAMILY_INVITATION",
    "typeDescription": "Family Invitation"
  }
}</div>
        </div>
        <div class="endpoint">
            <span class="method get">GET</span> <b>/api/notifications/since</b>
            <div>Get notifications for the current user since a given timestamp.</div>
            <ul>
                <li><b>Authentication:</b> Required</li>
                <li><b>Status Codes:</b> 200 OK, 400 Bad Request, 500 Error</li>
            </ul>
            <div class="code-label">Query Parameters:</div>
            <ul>
                <li><code>timestamp</code> (long, required) - Only notifications after this timestamp</li>
                <li><code>size</code> (integer, optional) - Page size (default: 50, max: 100)</li>
            </ul>
            <div class="code-label">Success Response (200):</div>
            <div class="sample-response">{
  "content": [ { /* Notification object */ } ],
  "page": 0,
  "size": 50,
  "totalElements": 10,
  "totalPages": 1,
  "isFirst": true,
  "isLast": true,
  "hasNext": false,
  "hasPrevious": false,
  "lastExpenseId": null
}</div>
        </div>
    </div>

    <h2>🗄️ Database Console:</h2>
    <p><a href="/h2-console" target="_blank">H2 Database Console</a></p>
</body>
</html>
        """.trimIndent()
    }
}
