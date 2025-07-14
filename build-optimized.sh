#!/bin/bash

# Optimized build script for Raspberry Pi
# This script implements several strategies to speed up JAR builds

set -e

echo "🚀 Starting optimized build for Raspberry Pi..."

# Enable Docker BuildKit for faster builds
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Clean up previous builds to free space
echo "🧹 Cleaning up previous builds..."
docker system prune -f --volumes
./gradlew clean

# Pre-warm Gradle daemon and download dependencies
echo "📦 Pre-warming Gradle and downloading dependencies..."
./gradlew --daemon dependencies --parallel --build-cache

# Build with optimized settings
echo "🔨 Building JAR with optimized settings..."
./gradlew bootJar \
  --daemon \
  --parallel \
  --build-cache \
  --max-workers=2 \
  --console=plain \
  -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=256m" \
  -Dkotlin.compiler.execution.strategy=in-process

# Build Docker image with simplified cache (compatible with default driver)
echo "🐳 Building Docker image..."
docker build \
  --platform linux/arm64 \
  --tag expense-tracker:latest \
  .

echo "✅ Build completed successfully!"
echo "📊 Build artifacts:"
ls -lah build/libs/

# Check if H2 server is running, if not start it
echo "🗄️ Checking H2 server status..."
H2_PORT=9092

# Function to check if port is in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0  # Port is in use
    else
        return 1  # Port is free
    fi
}

if check_port $H2_PORT; then
    echo "✅ H2 server is already running on port $H2_PORT"
else
    echo "⚠️ H2 server not running. Starting H2 server..."
    if [ -f "./h2-server_start_up.sh" ]; then
        chmod +x ./h2-server_start_up.sh
        ./h2-server_start_up.sh
        echo "✅ H2 server started"
    else
        echo "❌ h2-server_start_up.sh script not found!"
        exit 1
    fi
fi

# Start the application with docker-compose
echo "🚀 Starting application with docker-compose..."
docker-compose up

echo "🎉 All done! Your application is now running."
