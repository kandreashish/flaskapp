#!/bin/bash

echo "Fixing Kotlin compiler permission issues..."

# Clean up Kotlin compiler cache directories
echo "Cleaning Kotlin compiler cache..."
sudo rm -rf /home/pi/Desktop/flaskapp/.kotlin
sudo rm -rf /tmp/kotlin-*
sudo rm -rf /var/tmp/kotlin-*

# Clean Gradle cache if it exists
echo "Cleaning Gradle cache..."
sudo rm -rf /home/pi/.gradle/caches/
sudo rm -rf ./build/kotlin/

# Fix ownership of the entire project directory
echo "Fixing project directory ownership..."
sudo chown -R pi:pi /home/pi/Desktop/flaskapp/

# Set proper permissions
echo "Setting proper permissions..."
chmod -R 755 /home/pi/Desktop/flaskapp/
chmod +x /home/pi/Desktop/flaskapp/gradlew

# Clean Docker build cache to force fresh build
echo "Cleaning Docker build cache..."
docker system prune -f
docker builder prune -f

echo "Permission fixes completed!"
echo "You can now run: docker-compose build --no-cache expense-tracker"
