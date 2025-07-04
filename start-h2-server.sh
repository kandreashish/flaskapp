#!/bin/bash

echo "Starting H2 Database Server on port 9092..."

# Stop any existing Spring Boot application first
echo "Stopping any existing Spring Boot application..."
pkill -f "ExpenseTrackerApplicationKt" 2>/dev/null || true

# Create data directory if it doesn't exist and set proper permissions
echo "Setting up database directory with proper permissions..."
mkdir -p ./data/h2
chmod 755 ./data/h2
chmod 755 ./data

# Set write permissions for any existing database files
if [ -f "./data/h2/expensedb.mv.db" ]; then
    chmod 664 ./data/h2/expensedb.mv.db
    echo "Set write permissions for existing database file"
fi

if [ -f "./data/h2/expensedb.trace.db" ]; then
    chmod 664 ./data/h2/expensedb.trace.db
    echo "Set write permissions for existing trace file"
fi

echo "Starting H2 TCP Server..."
echo "Database files will be stored in: $(pwd)/data/h2"
echo "TCP Server will listen on port: 9092"
echo "Web Console will be available at: http://localhost:8082"
echo ""
echo "To stop the server, press Ctrl+C"
echo ""

# Start H2 server with explicit read-write access and create database if it doesn't exist
java -cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.2.224/7bdade27d8cd197d9b5ce9dc251f41d2edc5f7ad/h2-2.2.224.jar org.h2.tools.Server \
    -tcp -tcpAllowOthers -tcpPort 9092 \
    -web -webAllowOthers -webPort 8082 \
    -baseDir ./data/h2 \
    -ifNotExists \
    -tcpPassword ""
