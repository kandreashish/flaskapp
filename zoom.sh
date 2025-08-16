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

# Always do a clean build so changes are guaranteed to be compiled
echo "🧹 Running gradle clean..."
./gradlew clean

# Stop any running Gradle daemons to ensure a fresh build
./gradlew --stop || true

# Build fresh JAR (no conditional skipping)
echo "🔨 Building fresh JAR..."
./gradlew bootJar \
  --parallel \
  --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:G1HeapRegionSize=16m"

if [ $? -ne 0 ]; then
  echo "❌ Gradle build failed. Aborting."; exit 1; fi

# Optional: show artifact checksum to confirm change
ARTIFACT="build/libs/expense-tracker-0.0.11-SNAPSHOT.jar"
if [ -f "$ARTIFACT" ]; then
  echo "📦 Artifact: $ARTIFACT"
  (command -v sha256sum >/dev/null && sha256sum "$ARTIFACT") || (shasum -a 256 "$ARTIFACT")
else
  echo "❌ Expected artifact missing: $ARTIFACT"; exit 1
fi

# Force Docker image rebuild with no cache to ensure new JAR is baked in
echo "🐳 Rebuilding Docker image (no cache)..."
docker-compose build --no-cache expense-tracker

# Recreate container to pick up new image
echo "🚀 Restarting container..."
docker-compose up -d --force-recreate expense-tracker

# Tail logs briefly
echo "📋 Recent logs:"
docker-compose logs --tail=100 expense-tracker

echo "🎉 All done! Your application is now running."

echo "Storage: $(df -h / | awk 'NR==2 {print $2}')"; \
echo "RAM: $(free -h | awk '/^Mem:/ {print $2}')"; \
echo "Temperature: $(vcgencmd measure_temp)"