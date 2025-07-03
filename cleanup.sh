#!/bin/bash

# Kill any process running on port 8080
echo "Checking for processes on port 8080..."
PID=$(lsof -ti:8080)
if [ ! -z "$PID" ]; then
    echo "Killing process $PID on port 8080..."
    kill -9 $PID
    sleep 2
else
    echo "No process found on port 8080"
fi

# Clean up any locked database files
echo "Cleaning up database locks..."
if [ -d "data/h2" ]; then
    find data/h2 -name "*.lock.db" -delete 2>/dev/null || true
    echo "Database lock files cleaned"
fi

echo "Environment cleaned successfully!"
