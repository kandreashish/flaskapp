# Optimized Dockerfile for Raspberry Pi ARM64 - Fast Build
FROM --platform=linux/arm64 eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install only essential dependencies for Raspberry Pi
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        tini \
        curl \
        && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create app user for security
RUN groupadd -g 1000 appgroup && \
    useradd -r -u 1000 -g appgroup -d /app -s /bin/bash appuser

# Copy the pre-built JAR (build locally first with ./gradlew bootJar)
COPY build/libs/*-SNAPSHOT.jar app.jar

# Create necessary directories with proper permissions for Raspberry Pi
RUN mkdir -p /app/logs /app/data /app/uploads/profile-pics /app/tmp && \
    chown -R appuser:appgroup /app && \
    chmod -R 755 /app && \
    chmod 777 /app/logs /app/uploads /app/uploads/profile-pics

# Copy Firebase service account key if it exists
COPY src/main/resources/serviceAccountKey.json /app/serviceAccountKey.json
RUN chown appuser:appgroup /app/serviceAccountKey.json

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 3000

# Health check optimized for Raspberry Pi
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:3000/actuator/health || exit 1

# JVM settings optimized for Raspberry Pi ARM64
ENTRYPOINT ["tini", "--"]
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:+UseG1GC", \
     "-XX:G1HeapRegionSize=4m", \
     "-XX:+UseStringDeduplication", \
     "-XX:+OptimizeStringConcat", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-Dspring.profiles.active=prod", \
     "-jar", "app.jar"]
