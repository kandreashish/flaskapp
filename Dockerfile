# Stage 1: Build the application
# Using eclipse-temurin:17-jdk for ARM64 compatibility (smaller than gradle image)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

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
RUN ./gradlew clean bootJar --no-daemon --parallel --build-cache --console=plain

# Stage 2: Create the final runtime image
# Using standard JRE image with ARM64 support
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Install curl for health checks and create non-root user
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/* && \
    groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy the built JAR file from the 'build' stage
COPY --from=build /app/build/libs/*.jar app.jar

# Create a directory for the H2 database with proper ownership
RUN mkdir -p /app/h2-data && chown -R appuser:appgroup /app

# Switch to non-root user for security
USER appuser:appgroup

# Expose the port
EXPOSE 8080

# Define the command to run the application with JVM optimizations
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
