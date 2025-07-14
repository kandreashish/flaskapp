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

# Build Docker image with BuildKit optimizations
echo "🐳 Building Docker image..."
docker buildx build \
  --platform linux/arm64 \
  --cache-from type=local,src=/tmp/.buildx-cache \
  --cache-to type=local,dest=/tmp/.buildx-cache-new,mode=max \
  --tag expense-tracker:latest \
  .

# Move cache to avoid infinite growth
rm -rf /tmp/.buildx-cache
mv /tmp/.buildx-cache-new /tmp/.buildx-cache

echo "✅ Build completed successfully!"
echo "📊 Build artifacts:"
ls -lah build/libs/

echo "🎯 To start the application, run:"
echo "docker-compose up -d"
