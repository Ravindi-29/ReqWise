# Multi-stage build for ReqWise Spring Boot API
# Stage 1: Build JAR using Maven & Eclipse Temurin JDK 21
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy Maven POM and pre-download dependencies for caching
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and package executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal JRE 21 Runtime Image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create storage directory inside container
RUN mkdir -p /storage_data/raw_documents /storage_data/previews /storage_data/exports

# Copy built artifact from builder stage
COPY --from=builder /build/target/reqwise-api-0.1.0-SNAPSHOT.jar app.jar

# Expose Spring Boot HTTP port
EXPOSE 8080

# Environment variables with sensible defaults
ENV SPRING_PROFILES_ACTIVE=postgres \
    SERVER_PORT=8080 \
    REQWISE_STORAGE_BASE_DIR=/storage_data

# Launch Spring Boot with optimized container memory flags
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
