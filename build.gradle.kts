import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.4"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

group = "com.lavish"
version = "0.0.11-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

// Exclude only unnecessary logging dependencies, keep Guava for Firebase
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    exclude(group = "ch.qos.logback", module = "logback-classic")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") {
        exclude(group = "org.apache.tomcat", module = "tomcat-jdbc")
    }
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Firebase with explicit Guava dependency to prevent conflicts
    implementation("com.google.firebase:firebase-admin:9.5.0")
    implementation("com.google.guava:guava:32.1.3-jre") // Explicit Guava version compatible with Firebase

    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.3")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Use lighter logging
    implementation("org.springframework.boot:spring-boot-starter-log4j2")

    // Swagger/OpenAPI dependencies
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.3.0")

    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
        // Optimize for build speed on ARM
        freeCompilerArgs += listOf(
            "-language-version 2.0",
            "-Xbackend-threads=2"
        )
    }
}

// ✅ Enhanced Performance for Raspberry Pi - Adjusted for stability
tasks.withType<JavaCompile> {
    options.isIncremental = true
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("-Xmx1g", "-XX:MaxMetaspaceSize=512m")
}

tasks.withType<Jar> {
    enabled = true
    archiveClassifier.set("plain")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    launchScript()
    // Optimize JAR creation
    isZip64 = true
    archiveClassifier.set("")
}

// Add build cache configuration
tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = 1 // Limit for Raspberry Pi
    systemProperty("file.encoding", "UTF-8")
}
