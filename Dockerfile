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

# Build with optimized settings for Raspberry Pi
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/app/build/tmp \
    ./gradlew bootJar --no-daemon --parallel --build-cache --console=plain \
    --max-workers=3 \
    -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=256m" \
    -Dkotlin.compiler.execution.strategy=in-process

# Stage 3: Final runtime image optimized for ARM64/Raspberry Pi
FROM --platform=$TARGETPLATFORM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Install minimal dependencies for ARM64
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        tini \
        su-exec \
        && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create user with specific UID/GID
RUN groupadd -g 1000 appgroup && \
    useradd -r -u 1000 -g appgroup -d /app -s /bin/bash appuser

# Copy the built JAR from build stage
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Create directories with proper permissions BEFORE switching users
RUN mkdir -p /app/h2-data /app/logs /app/tmp /app/uploads /app/uploads/profile-pics && \
    chown -R appuser:appgroup /app && \
    chmod -R 755 /app && \
    chmod 755 /app/logs && \
    chmod 755 /app/uploads && \
    chmod 755 /app/uploads/profile-pics && \
    chmod 666 /app/logs/* 2>/dev/null || true

# Set proper ownership for mounted volumes at runtime
RUN echo '#!/bin/bash\n\
if [ -d "/app/uploads" ]; then\n\
    chown -R appuser:appgroup /app/uploads\n\
    chmod -R 755 /app/uploads\n\
fi\n\
exec "$@"' > /app/fix-permissions.sh && \
    chmod +x /app/fix-permissions.sh

# Switch to non-root user
USER appuser:appgroup

# Expose port
EXPOSE 3000

# Fixed JVM settings for Java 17 on Raspberry Pi
ENTRYPOINT ["tini", "--", "java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:G1HeapRegionSize=4m", \
    "-XX:+UseStringDeduplication", \
    "-XX:+OptimizeStringConcat", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.jmx.enabled=false", \
    "-Djava.io.tmpdir=/app/tmp", \
    "-jar", "app.jar"]
