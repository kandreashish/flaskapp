#!/bin/bash

# Kill any process running on port 3000
echo "Checking for processes on port 3000..."
PID=$(lsof -ti:3000)
if [ ! -z "$PID" ]; then
    echo "Killing process $PID on port 3000..."
    kill -9 $PID
    sleep 2
else
    echo "No process found on port 3000"
fi

# Clean up any locked database files
echo "Cleaning up database locks..."
if [ -d "data/h2" ]; then
    find data/h2 -name "*.lock.db" -delete 2>/dev/null || true
    echo "Database lock files cleaned"
fi

echo "Environment cleaned successfully!"
