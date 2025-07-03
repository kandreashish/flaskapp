#!/bin/bash

# Start script for the expense tracker application
echo "Starting Expense Tracker Application..."

# First, run cleanup
./cleanup.sh

# Check if gradlew is executable
if [ ! -x "./gradlew" ]; then
    chmod +x ./gradlew
fi

# Start the application
echo "Starting Spring Boot application..."
./gradlew bootRun
