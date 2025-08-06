#!/bin/bash

# Start script for the expense tracker application
echo "Starting Expense Tracker Application..."

# First, run cleanup
./cleanup.sh

# Load environment variables from .env file
echo "Loading environment variables..."
if [ -f "./load-env.sh" ]; then
    source ./load-env.sh
else
    echo "Warning: load-env.sh not found. Application may fail to start without environment variables."
fi

# Check if gradlew is executable
if [ ! -x "./gradlew" ]; then
    chmod +x ./gradlew
fi

# Start the application with environment variables
echo "Starting Spring Boot application..."
source ./load-env.sh
env | grep FIREBASE | xargs -I {} echo "Setting: {}"
./gradlew bootRun
