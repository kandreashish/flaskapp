# syntax=docker/dockerfile:1.4
# Stage 1: Dependencies cache layer
FROM eclipse-temurin:17-jdk AS dependencies
WORKDIR /app

# Copy only dependency-related files
COPY gradlew .
COPY gradlew.bat .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies in a separate layer for better caching
# This layer will only rebuild when dependencies change
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --console=plain

# Stage 2: Build application
FROM dependencies AS build
WORKDIR /app

# Copy source code (this layer changes frequently)
COPY src ./src

# Build with Gradle cache but without build directory cache to avoid conflicts
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --parallel --build-cache --console=plain

# Stage 3: Final runtime image optimized for ARM64/Raspberry Pi
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Install minimal dependencies for ARM64
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create user with specific UID/GID that matches common host user (1000:1000)
RUN groupadd -g 1000 appgroup && \
    useradd -r -u 1000 -g appgroup -d /app -s /bin/bash appuser

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Create data directory and set ownership BEFORE switching users
RUN mkdir -p /app/h2-data /app/logs && \
    chown -R 1000:1000 /app && \
    chmod -R 755 /app

# Switch to non-root user with consistent UID
USER 1000:1000

# Expose port
EXPOSE 3000

# Optimized JVM settings for Raspberry Pi (ARM64)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=70.0", \
    "-XX:+UseG1GC", \
    "-XX:G1HeapRegionSize=16m", \
    "-XX:+UseStringDeduplication", \
    "-XX:+OptimizeStringConcat", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.jmx.enabled=false", \
    "-jar", "app.jar"]
