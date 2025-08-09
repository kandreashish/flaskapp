#!/bin/bash

echo "Quick H2 Database Connection Test"
echo "================================="

# Test basic connectivity
echo "Testing connection to 103.80.162.46:9092..."

if command -v nc >/dev/null 2>&1; then
    if nc -zv 103.80.162.46 9092 2>&1; then
        echo "✅ SUCCESS: Port 9092 is accessible from this device"
        echo "The H2 database should be reachable"
    else
        echo "❌ FAILED: Cannot connect to port 9092"
        echo "This is likely why your application can't connect"
    fi
else
    echo "⚠️  netcat (nc) not available, trying alternative..."
    if timeout 5 bash -c "</dev/tcp/103.80.162.46/9092" 2>/dev/null; then
        echo "✅ SUCCESS: Port 9092 is accessible"
    else
        echo "❌ FAILED: Cannot connect to port 9092"
    fi
fi

echo ""
echo "If the test failed, run: ./debug-pi-connection.sh for detailed troubleshooting"
