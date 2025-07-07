# Expense Tracker Pagination API Examples

## Authentication Setup
First, you'll need to authenticate and get a valid token:

```bash
# Login to get JWT token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'

# Save the token from response for subsequent requests
export JWT_TOKEN="your_jwt_token_here"
```

## 1. Traditional Page-Based Pagination (Backward Compatible)

### Basic pagination with default parameters
```bash
curl -X GET "http://localhost:8080/api/expenses" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

### Page-based pagination with custom size
```bash
# First page with 20 items
curl -X GET "http://localhost:8080/api/expenses?page=0&size=20" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Second page with 20 items
curl -X GET "http://localhost:8080/api/expenses?page=1&size=20" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

### With sorting parameters
```bash
# Sort by amount in descending order
curl -X GET "http://localhost:8080/api/expenses?page=0&size=10&sortBy=amount&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Sort by date in ascending order
curl -X GET "http://localhost:8080/api/expenses?page=0&size=10&sortBy=date&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## 2. Offset-Based Pagination (Prevents Duplicates with Dynamic Page Sizes)

### Initial load with large page size
```bash
# Load first 50 items
curl -X GET "http://localhost:8080/api/expenses?offset=0&size=50" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

### Continue with different page size (NO DUPLICATES!)
```bash
# Load next 10 items starting from offset 50
curl -X GET "http://localhost:8080/api/expenses?offset=50&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Load next 25 items starting from offset 60
curl -X GET "http://localhost:8080/api/expenses?offset=60&size=25" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

### Offset-based with sorting
```bash
# Offset pagination with sorting by date
curl -X GET "http://localhost:8080/api/expenses?offset=0&size=30&sortBy=date&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## 3. Category-Based Pagination

```bash
# Get expenses by category with pagination
curl -X GET "http://localhost:8080/api/expenses/category/FOOD?page=0&size=15" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Get expenses by category (entertainment)
curl -X GET "http://localhost:8080/api/expenses/category/ENTERTAINMENT?page=0&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## 4. Date Range Pagination

```bash
# Get expenses between dates with pagination
curl -X GET "http://localhost:8080/api/expenses/between-dates?startDate=2024-01-01&endDate=2024-12-31&page=0&size=20" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Get expenses for current month
curl -X GET "http://localhost:8080/api/expenses/between-dates?startDate=2025-07-01&endDate=2025-07-31&page=0&size=50" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## 5. Family Expenses Pagination

```bash
# Get family expenses with pagination
curl -X GET "http://localhost:8080/api/expenses/family?page=0&size=25" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## 6. Edge Cases and Validation

### Test pagination parameter validation
```bash
# Negative page number (will be corrected to 0)
curl -X GET "http://localhost:8080/api/expenses?page=-1&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Zero page size (will default to 10)
curl -X GET "http://localhost:8080/api/expenses?page=0&size=0" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Large page size (will be capped at 100)
curl -X GET "http://localhost:8080/api/expenses?page=0&size=500" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"

# Negative offset (will be corrected to 0)
curl -X GET "http://localhost:8080/api/expenses?offset=-10&size=20" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## 7. Example Response Format

The API responses will include pagination metadata:

```json
{
  "content": [
    {
      "expenseId": "123e4567-e89b-12d3-a456-426614174000",
      "userId": "user123",
      "amount": 25.50,
      "category": "FOOD",
      "description": "Lunch at restaurant",
      "date": 1672531200000,
      "familyId": "family456",
      "expenseCreatedOn": 1672531200000,
      "lastModifiedOn": 1672531200000,
      "createdBy": "user123",
      "modifiedBy": "user123",
      "synced": true
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 150,
  "totalPages": 15,
  "isFirst": true,
  "isLast": false,
  "hasNext": true,
  "hasPrevious": false,
  "offset": 0
}
```

## 8. Best Practices for Dynamic Page Sizes

### ❌ Problem Scenario (Old Way)
```bash
# This creates duplicates when changing page sizes:
curl -X GET "http://localhost:8080/api/expenses?page=0&size=50"  # Gets items 0-49
curl -X GET "http://localhost:8080/api/expenses?page=1&size=10"  # Gets items 10-19 (DUPLICATES!)
```

### ✅ Solution Scenario (New Way)
```bash
# Use offset-based pagination for dynamic page sizes:
curl -X GET "http://localhost:8080/api/expenses?offset=0&size=50"   # Gets items 0-49
curl -X GET "http://localhost:8080/api/expenses?offset=50&size=10"  # Gets items 50-59 (NO DUPLICATES!)
```

## 9. Advanced Sorting Examples

```bash
# Sort by expense creation date (newest first)
curl -X GET "http://localhost:8080/api/expenses?offset=0&size=20&sortBy=expenseCreatedOn&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Sort by amount (highest first)
curl -X GET "http://localhost:8080/api/expenses?offset=0&size=15&sortBy=amount&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Sort by category alphabetically
curl -X GET "http://localhost:8080/api/expenses?offset=0&size=25&sortBy=category&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## 10. Monthly Summary

```bash
# Get monthly expense summary
curl -X GET "http://localhost:8080/api/expenses/monthly-sum?year=2025&month=7" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json"
```

## Testing the Duplicate Prevention

To test that the duplicate prevention works:

1. First, create some test data:
```bash
# Create a few expenses first
curl -X POST "http://localhost:8080/api/expenses" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 25.50,
    "category": "FOOD",
    "description": "Test expense 1",
    "date": 1672531200000
  }'
```

2. Then test the pagination:
```bash
# Get first 5 items (should see items 0-4)
curl -X GET "http://localhost:8080/api/expenses?offset=0&size=5" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.content[].description'

# Get next 3 items (should see items 5-7, NO duplicates from previous call)
curl -X GET "http://localhost:8080/api/expenses?offset=5&size=3" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.content[].description'
```

## Notes
- Replace `$JWT_TOKEN` with your actual JWT token
- Adjust the server URL if running on a different host/port
- The `jq` command is used for JSON formatting (install with `brew install jq` on macOS)
- All pagination parameters are validated server-side for security and performance
