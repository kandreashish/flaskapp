<!-- Favicon for browser tab -->
<link rel="icon" type="image/png" href="https://raw.githubusercontent.com/github/explore/main/topics/api/api.png" />

# Get Expenses Since Timestamp/Date - API Examples

## Authentication Setup
```bash
# Get JWT token first
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password123"}'

export JWT_TOKEN="your_jwt_token_here"
```

## 1. Get Expenses Since Last Modified Timestamp (For Sync)

### Basic Usage - Get expenses modified after a timestamp
```bash
# Get expenses modified after timestamp (useful for sync operations)
LAST_SYNC_TIME=1672531200000  # January 1, 2023

curl -X GET "http://localhost:8080/api/expenses/since?lastModified=$LAST_SYNC_TIME&size=20" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" | jq '.'
```

### With Cursor-based Pagination (No Duplicates)
```bash
# First request
RESPONSE1=$(curl -s -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=50" \
  -H "Authorization: Bearer $JWT_TOKEN")

LAST_ID1=$(echo $RESPONSE1 | jq -r '.lastExpenseId')
echo "First batch loaded. Last ID: $LAST_ID1"

# Next batch using cursor
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=10&lastExpenseId=$LAST_ID1" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.'
```

### Different Sort Orders for Sync
```bash
# Sort by last modified (newest first) - best for sync
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=20&sortBy=lastModifiedOn&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Sort by creation date (oldest first)
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=20&sortBy=expenseCreatedOn&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Sort by expense date (newest first)
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=20&sortBy=date&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## 2. Get Expenses Since a Specific Date

### Basic Usage - Get expenses from a date onwards
```bash
# Get all expenses from July 1, 2025 onwards
curl -X GET "http://localhost:8080/api/expenses/since-date?date=2025-07-01&size=25" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" | jq '.'
```

### With Cursor Pagination
```bash
# Load expenses since a specific date with pagination
RESPONSE=$(curl -s -X GET "http://localhost:8080/api/expenses/since-date?date=2025-06-01&size=30" \
  -H "Authorization: Bearer $JWT_TOKEN")

LAST_ID=$(echo $RESPONSE | jq -r '.lastExpenseId')

# Get next batch
curl -X GET "http://localhost:8080/api/expenses/since-date?date=2025-06-01&size=15&lastExpenseId=$LAST_ID" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.'
```

### Different Sort Orders
```bash
# Sort by date (oldest first) - chronological order
curl -X GET "http://localhost:8080/api/expenses/since-date?date=2025-07-01&size=20&sortBy=date&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Sort by amount (highest first)
curl -X GET "http://localhost:8080/api/expenses/since-date?date=2025-07-01&size=20&sortBy=amount&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Sort by last modified (for tracking changes)
curl -X GET "http://localhost:8080/api/expenses/since-date?date=2025-07-01&size=20&sortBy=lastModifiedOn&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## 3. Real-World Use Cases

### Mobile App Sync (Get Changes Since Last Sync)
```bash
# Store last sync timestamp on client
LAST_SYNC=$(date -d "2025-07-06 10:00:00" +%s)000  # Convert to milliseconds

# Get all changes since last sync
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=$LAST_SYNC&size=100&sortBy=lastModifiedOn&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.content[] | {id: .expenseId, amount: .amount, description: .description, lastModified: .lastModifiedOn}'

# Update client's last sync timestamp to current time
NEW_SYNC=$(date +%s)000
echo "Update client last sync to: $NEW_SYNC"
```

### Get Today's Expenses
```bash
# Get all expenses from today onwards
TODAY=$(date +%Y-%m-%d)
curl -X GET "http://localhost:8080/api/expenses/since-date?date=$TODAY&size=50" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.content[] | {amount: .amount, description: .description, date: .date}'
```

### Get This Week's Expenses
```bash
# Get expenses from start of this week
WEEK_START=$(date -d "last monday" +%Y-%m-%d)
curl -X GET "http://localhost:8080/api/expenses/since-date?date=$WEEK_START&size=100&sortBy=date&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Get This Month's Expenses
```bash
# Get expenses from start of current month
MONTH_START=$(date +%Y-%m-01)
curl -X GET "http://localhost:8080/api/expenses/since-date?date=$MONTH_START&size=200&sortBy=date&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## 4. Error Handling

### Invalid Date Format
```bash
curl -X GET "http://localhost:8080/api/expenses/since-date?date=invalid-date&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
# Returns: 400 Bad Request with "Invalid date format. Use YYYY-MM-DD format"
```

### Invalid Timestamp
```bash
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=invalid&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
# Returns: 400 Bad Request
```

### Invalid Cursor ID
```bash
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=10&lastExpenseId=invalid-id" \
  -H "Authorization: Bearer $JWT_TOKEN"
# Returns: 404 with "Last expense with ID 'invalid-id' not found"
```

## 5. Response Format Examples

### Success Response
```json
{
  "content": [
    {
      "expenseId": "123e4567-e89b-12d3-a456-426614174000",
      "userId": "user123",
      "amount": 25.50,
      "category": "FOOD",
      "description": "Lunch at restaurant",
      "date": 1720339200000,
      "lastModifiedOn": 1720339200000,
      "expenseCreatedOn": 1720339200000
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 150,
  "totalPages": -1,
  "isFirst": true,
  "isLast": false,
  "hasNext": true,
  "hasPrevious": false,
  "lastExpenseId": "123e4567-e89b-12d3-a456-426614174000"
}
```

## 6. Performance Tips

### For Sync Operations (Best Performance)
```bash
# Use lastModifiedOn for sync - most efficient
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=100&sortBy=lastModifiedOn&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### For Date-based Queries
```bash
# Use date field for date-based queries
curl -X GET "http://localhost:8080/api/expenses/since-date?date=2025-07-01&size=100&sortBy=date&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Optimal Page Sizes
```bash
# Small batches for real-time updates
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Large batches for initial sync
curl -X GET "http://localhost:8080/api/expenses/since?lastModified=1672531200000&size=100" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## Key Benefits

✅ **Efficient Sync**: Get only changed data since last sync
✅ **No Duplicates**: Cursor-based pagination prevents duplicates
✅ **Flexible Dates**: Support both timestamps and human-readable dates
✅ **Multiple Sort Orders**: Sort by date, modification time, or amount
✅ **Performance**: Optimized queries with proper indexing
✅ **Error Handling**: Clear error messages for invalid inputs
