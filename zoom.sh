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

# Detect docker-compose v1 and disable BuildKit (avoids 'ContainerConfig' KeyError bug)
if docker-compose version 2>/dev/null | grep -q 'version 1.'; then
  echo "⚠️ docker-compose v1 detected; disabling BuildKit for compatibility"
  export DOCKER_BUILDKIT=0
fi


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

# After successful gradle bootJar build, replace artifact handling block
ARTIFACT_PATTERN="build/libs/*-SNAPSHOT.jar"
JAR_FILE=$(ls $ARTIFACT_PATTERN 2>/dev/null | head -n1 || true)
if [ -z "$JAR_FILE" ]; then
  echo "❌ No JAR found matching $ARTIFACT_PATTERN"; exit 1
fi
ln -sf "$(basename "$JAR_FILE")" build/libs/app.jar
echo "📦 Using JAR: $JAR_FILE"
(command -v sha256sum >/dev/null && sha256sum "$JAR_FILE") || (shasum -a 256 "$JAR_FILE")

# Ensure build/libs/app.jar exists and is a valid file before Docker build
if [ ! -f build/libs/app.jar ]; then
  echo "❌ build/libs/app.jar does not exist or is not a valid file. Aborting Docker build."; exit 1
fi

# Prefer docker compose (v2) if available
if command -v docker >/dev/null && docker compose version >/dev/null 2>&1; then
  DC='docker compose'
else
  DC='docker-compose'
fi

# Rebuild image without cache to ensure new JAR is copied
echo "🐳 Rebuilding Docker image (no cache)..."
$DC build --no-cache expense-tracker

# Remove old container explicitly to avoid stale metadata issues
$DC rm -f expense-tracker 2>/dev/null || true

# Recreate container
echo "🚀 Restarting container..."
$DC up -d --force-recreate expense-tracker

# Logs
echo "📋 Recent logs:"
$DC logs --tail=100 expense-tracker

echo "🎉 All done! Your application is now running."

echo "Storage: $(df -h / | awk 'NR==2 {print $2}')"; \
echo "RAM: $(free -h | awk '/^Mem:/ {print $2}')"; \
echo "Temperature: $(vcgencmd measure_temp)"