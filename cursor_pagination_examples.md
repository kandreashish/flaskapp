# Cursor-Based Pagination with Expense ID - Curl Examples

## Authentication Setup
```bash
# Get JWT token first
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password123"}'

export JWT_TOKEN="your_jwt_token_here"
```

## Cursor-Based Pagination (Prevents Duplicates with Dynamic Page Sizes)

### 1. Initial Load (No Cursor)
```bash
# First request - get initial 50 expenses
curl -X GET "http://localhost:8080/api/expenses?size=50" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" | jq '.'
```

**Response includes:**
```json
{
  "content": [...],
  "page": 0,
  "size": 50,
  "totalElements": 500,
  "totalPages": 10,
  "isFirst": true,
  "isLast": false,
  "hasNext": true,
  "hasPrevious": false,
  "lastExpenseId": "expense-id-50"  // Use this for next request
}
```

### 2. Load Next 10 Items (Dynamic Page Size - NO DUPLICATES!)
```bash
# Use the lastExpenseId from previous response
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=expense-id-50&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" | jq '.'
```

**Response:**
```json
{
  "content": [...],  // Items 51-60 (NO duplicates!)
  "size": 10,
  "hasNext": true,
  "lastExpenseId": "expense-id-60"  // Use this for next request
}
```

### 3. Continue with Different Page Size
```bash
# Load next 25 items starting from the last expense ID
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=expense-id-60&size=25" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" | jq '.'
```

**Response:**
```json
{
  "content": [...],  // Items 61-85 (seamless continuation!)
  "size": 25,
  "hasNext": true,
  "lastExpenseId": "expense-id-85"
}
```

## Cursor-Based Pagination with Different Sort Orders

### Sort by Date (Newest First)
```bash
# Initial load sorted by date (newest first)
curl -X GET "http://localhost:8080/api/expenses?size=20&sortBy=date&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.lastExpenseId'

# Next batch using cursor
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=EXPENSE_ID_FROM_ABOVE&size=10&sortBy=date&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Sort by Amount (Highest First)
```bash
# Initial load sorted by amount (highest first)
curl -X GET "http://localhost:8080/api/expenses?size=15&sortBy=amount&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.lastExpenseId'

# Next batch using cursor
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=EXPENSE_ID_FROM_ABOVE&size=10&sortBy=amount&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Sort by Creation Date (Oldest First)
```bash
# Initial load sorted by creation date (oldest first)
curl -X GET "http://localhost:8080/api/expenses?size=30&sortBy=expenseCreatedOn&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.lastExpenseId'

# Next batch using cursor
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=EXPENSE_ID_FROM_ABOVE&size=20&sortBy=expenseCreatedOn&isAsc=true" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## Complete Example: Loading Data with Dynamic Page Sizes

```bash
# Step 1: Load initial batch (large size for performance)
RESPONSE1=$(curl -s -X GET "http://localhost:8080/api/expenses?size=100&sortBy=date&isAsc=false" \
  -H "Authorization: Bearer $JWT_TOKEN")

LAST_ID1=$(echo $RESPONSE1 | jq -r '.lastExpenseId')
echo "First batch loaded. Last ID: $LAST_ID1"

# Step 2: Load smaller batch for mobile/detail view
RESPONSE2=$(curl -s -X GET "http://localhost:8080/api/expenses?lastExpenseId=$LAST_ID1&size=5" \
  -H "Authorization: Bearer $JWT_TOKEN")

LAST_ID2=$(echo $RESPONSE2 | jq -r '.lastExpenseId')
echo "Second batch loaded. Last ID: $LAST_ID2"

# Step 3: Load medium batch for tablet view
RESPONSE3=$(curl -s -X GET "http://localhost:8080/api/expenses?lastExpenseId=$LAST_ID2&size=20" \
  -H "Authorization: Bearer $JWT_TOKEN")

LAST_ID3=$(echo $RESPONSE3 | jq -r '.lastExpenseId')
echo "Third batch loaded. Last ID: $LAST_ID3"

# No duplicates across any of these requests!
```

## Backward Compatibility

Traditional page-based pagination still works:

```bash
# Old way (still supported)
curl -X GET "http://localhost:8080/api/expenses?page=0&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN"

curl -X GET "http://localhost:8080/api/expenses?page=1&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## Error Handling

### Invalid Expense ID
```bash
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=invalid-id&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
# Returns: 404 with "Last expense with ID 'invalid-id' not found"
```

### Access to Other User's Expense ID
```bash
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=other-users-expense&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
# Returns: 403 with "Access denied to expense 'other-users-expense'"
```

## Key Benefits of This Approach

✅ **No Duplicates**: Cursor-based pagination eliminates duplicate entries
✅ **Dynamic Page Sizes**: Change page size between requests without issues
✅ **Performance**: Efficient database queries using indexed fields
✅ **Consistency**: Results remain consistent even if data changes
✅ **Backward Compatible**: Old pagination still works

## Usage Pattern for Mobile Apps

```bash
# Mobile app pattern - variable page sizes based on UI state
# Initial load (splash screen)
curl -X GET "http://localhost:8080/api/expenses?size=50" -H "Authorization: Bearer $JWT_TOKEN"

# User scrolls (infinite scroll with smaller batches)
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=expense-50&size=10" -H "Authorization: Bearer $JWT_TOKEN"

# User switches to detail view (even smaller batches)
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=expense-60&size=3" -H "Authorization: Bearer $JWT_TOKEN"

# User switches back to list view (larger batches)
curl -X GET "http://localhost:8080/api/expenses?lastExpenseId=expense-63&size=25" -H "Authorization: Bearer $JWT_TOKEN"
```

No duplicates or gaps regardless of the page size changes!
