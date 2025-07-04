# Stage 1: Build the application
# Using eclipse-temurin:17-jdk-alpine for a much smaller build image (~300MB vs 1GB+)
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Install only essential build tools
RUN apk add --no-cache bash

# Copy Gradle wrapper and configuration files first for better caching
COPY gradlew .
COPY gradlew.bat .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Grant execute permissions to the Gradle wrapper script
RUN chmod +x gradlew

# Download and cache dependencies first (this layer will be cached unless dependencies change)
# This is the key optimization - dependencies are cached separately from source code
RUN ./gradlew dependencies --no-daemon || true

# Copy source code last so changes don't invalidate dependency cache
COPY src ./src

# Build the application with optimizations
RUN ./gradlew clean bootJar --no-daemon --parallel --build-cache

# Stage 2: Create the final runtime image
# Using alpine variant for smaller size (~200MB vs ~400MB)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl for healthcheck with minimal footprint
RUN apk add --no-cache curl

# Copy the built JAR file from the 'build' stage
COPY --from=build /app/build/libs/*.jar app.jar

# Create a directory for the H2 database
RUN mkdir -p /h2-data

# Expose the port
EXPOSE 8080

# Define the command to run the application with JVM optimizations
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
