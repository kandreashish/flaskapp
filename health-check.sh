#!/bin/bash

# Health check script for the expense tracker application
echo "=== Expense Tracker Health Check ==="

# Check if port 3000 is in use
PORT_CHECK=$(lsof -ti:3000)
if [ ! -z "$PORT_CHECK" ]; then
    echo "✅ Application is running on port 3000 (PID: $PORT_CHECK)"

    # Test the server response
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000)
    if [ "$RESPONSE" -eq 403 ] || [ "$RESPONSE" -eq 200 ]; then
        echo "✅ Server is responding (HTTP $RESPONSE)"
    else
        echo "⚠️  Server responded with HTTP $RESPONSE"
    fi

    # Test login endpoint
    LOGIN_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:3000/api/auth/login -H "Content-Type: application/json" -d '{}')
    if [ "$LOGIN_RESPONSE" -eq 400 ] || [ "$LOGIN_RESPONSE" -eq 401 ]; then
        echo "✅ Login endpoint is accessible (HTTP $LOGIN_RESPONSE)"
    else
        echo "⚠️  Login endpoint responded with HTTP $LOGIN_RESPONSE"
    fi
else
    echo "❌ No application running on port 3000"
fi

# Check database files
if [ -f "data/h2/expensedb.mv.db" ]; then
    echo "✅ Database file exists"

    # Check for lock files
    LOCK_FILES=$(find data/h2 -name "*.lock.db" 2>/dev/null | wc -l)
    if [ "$LOCK_FILES" -eq 0 ]; then
        echo "✅ No database lock files found"
    else
        echo "⚠️  Found $LOCK_FILES database lock file(s)"
    fi
else
    echo "⚠️  Database file not found"
fi

# Check log for recent errors
if [ -f "logs/spring.log" ]; then
    RECENT_ERRORS=$(tail -n 100 logs/spring.log | grep -i error | wc -l)
    if [ "$RECENT_ERRORS" -eq 0 ]; then
        echo "✅ No recent errors in logs"
    else
        echo "⚠️  Found $RECENT_ERRORS recent error(s) in logs"
    fi
fi

echo "=== Health Check Complete ==="
