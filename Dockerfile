# syntax=docker/dockerfile:1.4
# Stage 1: Dependencies cache layer
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jdk-jammy AS dependencies
WORKDIR /app

# Copy only dependency-related files
COPY gradlew .
COPY gradlew.bat .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies with ARM-optimized JVM settings
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --console=plain \
    --parallel --build-cache \
    -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=256m"

# Stage 2: Build application
FROM --platform=$TARGETPLATFORM dependencies AS build
WORKDIR /app

# Copy source code (this layer changes frequently)
COPY src ./src

# Build with optimized settings for Raspberry Pi - fix max-workers to match gradle.properties
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/app/build/tmp \
    ./gradlew bootJar --no-daemon --parallel --build-cache --console=plain \
    --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=256m" \
    -Dkotlin.compiler.execution.strategy=in-process

# Stage 3: Final runtime image optimized for ARM64/Raspberry Pi
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Install minimal dependencies for ARM64 - fix package installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        tini \
        gosu \
        && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    # Verify gosu installation
    gosu nobody true

# Create user with specific UID/GID - fix permission issues
RUN groupadd -g 1000 appgroup && \
    useradd -r -u 1000 -g appgroup -d /app -s /bin/bash appuser && \
    # Ensure the user can actually be used
    echo "appuser:x:1000:1000::/app:/bin/bash" >> /etc/passwd || true

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Create directories with proper permissions BEFORE switching users
RUN mkdir -p /app/h2-data /app/logs /app/tmp /app/uploads /app/uploads/profile-pics && \
    chown -R appuser:appgroup /app && \
    chmod -R 755 /app && \
    chmod 777 /app/logs && \
    chmod 777 /app/uploads && \
    chmod 777 /app/uploads/profile-pics && \
    chmod 666 /app/logs/* 2>/dev/null || true

# Create entrypoint script to handle permissions properly
RUN echo '#!/bin/bash\n\
set -e\n\
\n\
# Ensure directories exist and have correct permissions\n\
mkdir -p /app/uploads/profile-pics /app/logs /app/tmp\n\
\n\
# Fix ownership and permissions for mounted volumes\n\
if [ "$(id -u)" = "0" ]; then\n\
    # Running as root, fix permissions for mounted directories\n\
    if [ -d "/app/uploads" ]; then\n\
        chown -R appuser:appgroup /app/uploads 2>/dev/null || true\n\
        chmod -R 755 /app/uploads 2>/dev/null || true\n\
        # Ensure profile-pics subdirectory exists and is writable\n\
        mkdir -p /app/uploads/profile-pics\n\
        chown -R appuser:appgroup /app/uploads/profile-pics 2>/dev/null || true\n\
        chmod -R 777 /app/uploads/profile-pics 2>/dev/null || true\n\
    fi\n\
    if [ -d "/app/logs" ]; then\n\
        chown -R appuser:appgroup /app/logs 2>/dev/null || true\n\
        chmod -R 755 /app/logs 2>/dev/null || true\n\
    fi\n\
    # Use gosu to switch to appuser and execute Java\n\
    exec gosu appuser java \\\n\
        -XX:+UseContainerSupport \\\n\
        -XX:MaxRAMPercentage=75.0 \\\n\
        -XX:+UseG1GC \\\n\
        -XX:G1HeapRegionSize=4m \\\n\
        -XX:+UseStringDeduplication \\\n\
        -XX:+OptimizeStringConcat \\\n\
        -Djava.security.egd=file:/dev/./urandom \\\n\
        -Dspring.jmx.enabled=false \\\n\
        -Djava.io.tmpdir=/app/tmp \\\n\
        -jar app.jar\n\
else\n\
    # Already running as non-root user\n\
    # Still try to create directories in case they dont exist\n\
    mkdir -p /app/uploads/profile-pics 2>/dev/null || true\n\
    exec java \\\n\
        -XX:+UseContainerSupport \\\n\
        -XX:MaxRAMPercentage=75.0 \\\n\
        -XX:+UseG1GC \\\n\
        -XX:G1HeapRegionSize=4m \\\n\
        -XX:+UseStringDeduplication \\\n\
        -XX:+OptimizeStringConcat \\\n\
        -Djava.security.egd=file:/dev/./urandom \\\n\
        -Dspring.jmx.enabled=false \\\n\
        -Djava.io.tmpdir=/app/tmp \\\n\
        -jar app.jar\n\
fi' > /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh

# Expose port
EXPOSE 3000

# Use tini and our custom entrypoint
ENTRYPOINT ["tini", "--", "/app/entrypoint.sh"]
