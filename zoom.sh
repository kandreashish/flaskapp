#!/bin/bash

# Optimized build script for Raspberry Pi with improved caching
# This script implements advanced caching strategies for faster builds

echo "Storage: $(df -h / | awk 'NR==2 {print $2}')"; \
echo "RAM: $(free -h | awk '/^Mem:/ {print $2}')"; \
echo "Temperature: $(vcgencmd measure_temp)"

echo "🚀 Starting optimized build with enhanced caching..."

set -e

# Enable Docker BuildKit for faster builds
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1


# Check for changes before pulling
echo "🔍 Checking for remote changes..."
git fetch
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse @{u})

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "📥 Pulling latest changes..."
    git pull
else
    echo "✅ Already up to date - preserving cache"
fi

echo "RAM: $(free -h | awk '/^Mem:/ {print $2}')"; \
echo "Temperature: $(vcgencmd measure_temp)"

# Clean up Docker resources (preserve Gradle cache)
echo "🧹 Cleaning up Docker resources..."
docker container prune -f
docker image prune -f --filter "dangling=true"

# Compute a hash of all relevant source and config files
HASH_FILE=".last_build_hash"
CURRENT_HASH=$(find src/ build.gradle.kts settings.gradle.kts Dockerfile docker-compose.yml -type f -exec sha256sum {} + | sort | sha256sum | awk '{print $1}')

if [ -f "$HASH_FILE" ]; then
    LAST_HASH=$(cat "$HASH_FILE")
else
    LAST_HASH=""
fi

if [ "$CURRENT_HASH" != "$LAST_HASH" ]; then
    echo "🔄 Source/config files changed. Running clean build..."
    # Always clean before building to force a full rebuild
    echo "🧹 Running gradle clean..."
    ./gradlew clean

    # Stop any running Gradle daemons to ensure a fresh build
    ./gradlew --stop

    # Build with enhanced caching settings
    echo "🔨 Building JAR with enhanced caching..."
    ./gradlew bootJar \
      --daemon \
      --parallel \
      --build-cache \
      --configuration-cache \
      --configuration-cache-problems=warn \
      --max-workers=2 \
      --console=plain \
      -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:G1HeapRegionSize=16m" \
      -Dkotlin.compiler.execution.strategy=in-process \
      -Dkotlin.incremental=true \
      -Dorg.gradle.caching=true \
      -Dorg.gradle.configuration-cache=true

    # Check build success and cache status
    if [ $? -eq 0 ]; then
        echo "$CURRENT_HASH" > "$HASH_FILE"
        echo "✅ Build completed and hash updated!"
        # Display cache statistics if available
        if [ -d ".gradle/configuration-cache" ]; then
            echo "📊 Configuration cache is active"
        fi
    else
        echo "❌ Build failed, hash not updated."
        exit 1
    fi
else
    echo "⏩ No changes detected in source/config files. Skipping build."
fi

# Build Docker image with simplified cache (compatible with default driver)
echo "🐳 Building Docker image..."
docker build \
  --platform linux/arm64 \
  --tag expense-tracker:latest \
  .

echo "✅ Build completed successfully!"
echo "📊 Build artifacts:"
ls -lah build/libs/


echo "Temperature: $(vcgencmd measure_temp)"

# Start the application with docker-compose
echo "🚀 Starting application with docker-compose..."
docker-compose down

# Start the expense-tracker
echo "🚀 Starting expense-tracker..."
docker-compose up -d expense-tracker

# Follow logs for the service
echo "📋 Following logs... (Press Ctrl+C to stop following logs)"
docker-compose logs -f expense-tracker

echo "🎉 All done! Your application is now running."

echo "Storage: $(df -h / | awk 'NR==2 {print $2}')"; \
echo "RAM: $(free -h | awk '/^Mem:/ {print $2}')"; \
echo "Temperature: $(vcgencmd measure_temp)"