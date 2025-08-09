#!/bin/bash

# Optimized build script for Raspberry Pi with improved caching
# This script implements advanced caching strategies for faster builds

echo "Storage: $(df -h / | awk 'NR==2 {print $2}')"; \
echo "RAM: $(free -h | awk '/^Mem:/ {print $2}')"; \
echo "Temperature: $(vcgencmd measure_temp)"

echo "🚀 Starting optimized build with enhanced caching..."

set -e

# Load environment variables for Docker H2 configuration
echo "📋 Loading environment variables..."
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
    # Override with Docker H2 configuration
    export SPRING_DATASOURCE_URL="jdbc:h2:tcp://localhost:9092/expensedb"
    export SPRING_DATASOURCE_USERNAME="sa"
    export SPRING_DATASOURCE_PASSWORD=""
    export SPRING_DATASOURCE_DRIVER="org.h2.Driver"
    echo "✅ Environment configured for Docker H2"
else
    echo "⚠️  Warning: .env file not found, using defaults"
fi

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

# Only clean if necessary (check for significant changes)
if [ -f ".gradle-clean-needed" ] || [ ! -d "build" ]; then
    echo "🧹 Running gradle clean..."
    ./gradlew clean
    rm -f .gradle-clean-needed
else
    echo "⚡ Skipping clean to preserve cache"
fi

echo "RAM: $(free -h | awk '/^Mem:/ {print $2}')"; \
echo "Temperature: $(vcgencmd measure_temp)"

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
    echo "✅ Build completed successfully with caching!"
    # Display cache statistics if available
    if [ -d ".gradle/configuration-cache" ]; then
        echo "📊 Configuration cache is active"
    fi
else
    echo "❌ Build failed, marking for clean next time"
    touch .gradle-clean-needed
    exit 1
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

# Start H2 database first
echo "🗄️  Starting H2 Database..."
docker-compose up -d h2-database

# Wait for H2 to be ready
echo "⏳ Waiting for H2 database to be ready..."
sleep 5

# Test H2 connection
if nc -z localhost 9092 2>/dev/null; then
    echo "✅ H2 database is ready at localhost:9092"
else
    echo "⚠️  H2 database may still be starting up..."
    sleep 3
fi

# Start the application with docker-compose
echo "🚀 Starting application with docker-compose..."
docker-compose down --remove-orphans
docker-compose up -d

# Wait for application to start
echo "⏳ Waiting for application to start..."
sleep 10

# Check application status
echo "📊 Service Status:"
if nc -z localhost 9092 2>/dev/null; then
    echo "✅ H2 Database: Running (localhost:9092)"
else
    echo "❌ H2 Database: Not accessible"
fi

if nc -z localhost 8082 2>/dev/null; then
    echo "✅ H2 Web Console: Running (localhost:8082)"
else
    echo "❌ H2 Web Console: Not accessible"
fi

if nc -z localhost 3000 2>/dev/null; then
    echo "✅ Application: Running (localhost:3000)"
else
    echo "❌ Application: Not accessible"
fi

echo ""
echo "🌐 Service URLs:"
echo "📱 Application: http://localhost:3000"
echo "🗄️  H2 Console: http://localhost:8082"
echo "📊 Database URL: jdbc:h2:tcp://localhost:9092/expensedb"
echo "👤 DB Credentials: Username: sa, Password: (empty)"

echo ""
echo "Final Temperature: $(vcgencmd measure_temp)"
