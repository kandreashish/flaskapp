# Stage 1: Build the application
# Using gradle:8.5-jdk17 as the build environment.
# This image already contains Gradle 8.5 and JDK 17,
# so we align our project's wrapper to use 8.5 to avoid re-downloading.
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy necessary Gradle wrapper files and project configuration
# This ensures the Gradle wrapper can function correctly and use the Gradle version
# provided by the base image (8.5), or download it if not present/cached.
COPY gradlew .
COPY gradlew.bat .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy your source code
COPY src ./src

# Grant execute permissions to the Gradle wrapper script
# This is essential for ./gradlew to run.
RUN chmod +x gradlew

# Build the application into a JAR file.
# --no-daemon is good practice in Docker builds as daemons aren't needed across builds.
RUN ./gradlew clean bootJar --no-daemon

# Stage 2: Create the final runtime image
# Using eclipse-temurin:17-jre-jammy for a smaller runtime footprint,
# as it only includes the JRE, not the full JDK.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for healthcheck purposes.
# apt-get update is followed by apt-get install and then rm -rf /var/lib/apt/lists/*
# to clean up package lists, reducing the final image size.
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built JAR file from the 'build' stage into the runtime image.
# The JAR is typically found in /app/build/libs/ within the build stage.
# We rename it to app.jar for simplicity.
COPY --from=build /app/build/libs/*.jar app.jar

# Create a directory for the H2 database.
# This ensures that if your H2 database is configured to store files,
# it has a designated persistent location within the container's filesystem.
RUN mkdir -p /h2-data

# Expose the port on which your Spring Boot application will run.
# This informs Docker that the container listens on this port.
EXPOSE 8080

# Define the command to run the application when the container starts.
# java -jar app.jar is the standard way to run a Spring Boot executable JAR.
ENTRYPOINT ["java", "-jar", "app.jar"]