#!/bin/bash

echo "Starting H2 Database Server on port 9092..."

# Stop any existing Spring Boot application first
echo "Stopping any existing Spring Boot application..."
pkill -f "ExpenseTrackerApplicationKt" 2>/dev/null || true

# Create data directory if it doesn't exist
mkdir -p ./data/h2

echo "Starting H2 TCP Server..."
echo "Database files will be stored in: $(pwd)/data/h2"
echo "TCP Server will listen on port: 9092"
echo "Web Console will be available at: http://localhost:8082"
echo ""
echo "To stop the server, press Ctrl+C"
echo ""

# Start H2 server directly using the H2 jar file
java -cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.2.224/7bdade27d8cd197d9b5ce9dc251f41d2edc5f7ad/h2-2.2.224.jar org.h2.tools.Server -tcp -tcpAllowOthers -tcpPort 9092 -web -webAllowOthers -webPort 8082 -baseDir ./data/h2 -ifNotExists
