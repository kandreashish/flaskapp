#!/bin/bash

# Test script for sending notifications via your expense tracker API

# Base URL for your API
BASE_URL="http://localhost:3000/api"

# Example JWT token (replace with actual token)
JWT_TOKEN="your_jwt_token_here"

# Example expense ID (replace with actual expense ID)
EXPENSE_ID="your_expense_id_here"

echo "=== Testing Expense Notification ==="
echo "Sending notification for expense..."

curl -X POST \
  "$BASE_URL/expenses/notify" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "expenseId": "'$EXPENSE_ID'"
  }' \
  -v

echo -e "\n\n=== Testing Create Expense (Auto Notification) ==="
echo "Creating new expense (will automatically send notification)..."

curl -X POST \
  "$BASE_URL/expenses" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "description": "Test Expense",
    "amount": 50.00,
    "category": "Food",
    "notes": "Testing notification"
  }' \
  -v

echo -e "\n\n=== Direct FCM Test (if you have FCM server key) ==="
echo "Direct Firebase Cloud Messaging test..."

# FCM Server Key (replace with your actual FCM server key)
FCM_SERVER_KEY="your_fcm_server_key_here"
# Device FCM Token (replace with actual device token)
DEVICE_TOKEN="your_device_fcm_token_here"

curl -X POST \
  "https://fcm.googleapis.com/fcm/send" \
  -H "Content-Type: application/json" \
  -H "Authorization: key=$FCM_SERVER_KEY" \
  -d '{
    "to": "'$DEVICE_TOKEN'",
    "notification": {
      "title": "New Expense Added",
      "body": "Someone added a new expense"
    },
    "data": {
      "type": "expense",
      "title": "New Expense Added",
      "body": "you added a new expense",
      "amount": "$50.00"
    }
  }' \
  -v

echo -e "\n\nDone!"
