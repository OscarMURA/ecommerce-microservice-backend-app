# Multi-stage build for optimized Docker image
# Stage 1: Build stage - optimized with dependency caching
FROM maven:3.8.4-openjdk-11-slim AS builder
WORKDIR /build

# Copy only this service's files
COPY cloud-config/pom.xml pom.xml
COPY cloud-config/src src/

# Build only cloud-config
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime stage - security hardened
FROM openjdk:11-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

ARG PROJECT_VERSION=0.1.0
ARG ENVIRONMENT=dev
ARG USER_ID=1001
ARG GROUP_ID=1001

ENV SPRING_PROFILES_ACTIVE=${ENVIRONMENT}
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport"
ENV SERVER_PORT=9296

# Create non-root user for security
RUN groupadd -g ${GROUP_ID} appuser && \
    useradd -r -u ${USER_ID} -g appuser appuser

# Create app directory with proper permissions
RUN mkdir -p /home/app && \
    chown -R appuser:appuser /home/app

WORKDIR /home/app
USER appuser

# Copy JAR from builder stage
COPY --from=builder --chown=appuser:appuser /build/target/cloud-config-v${PROJECT_VERSION}.jar cloud-config.jar

EXPOSE ${SERVER_PORT}

# Health check with proper service context
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -Dserver.port=$SERVER_PORT -Dmanagement.server.port=$SERVER_PORT -jar cloud-config.jar"]
