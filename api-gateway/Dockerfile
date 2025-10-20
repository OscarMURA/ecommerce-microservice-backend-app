FROM maven:3.8.4-openjdk-11-slim AS build
WORKDIR /app

# Copy only this service's files
COPY api-gateway/pom.xml pom.xml
COPY api-gateway/src src/

# Build the service
RUN mvn clean package -DskipTests -q

FROM openjdk:11-jre-slim

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

ARG PROJECT_VERSION=0.1.0
ARG ENVIRONMENT=dev
ARG USER_ID=1001
ARG GROUP_ID=1001

ENV SPRING_PROFILES_ACTIVE=${ENVIRONMENT}
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport"
ENV SERVER_PORT=8080

RUN groupadd -g ${GROUP_ID} appuser && \
    useradd -r -u ${USER_ID} -g appuser appuser

RUN mkdir -p /home/app && \
    chown -R appuser:appuser /home/app

WORKDIR /home/app
USER appuser

COPY --from=build --chown=appuser:appuser /app/target/api-gateway-v${PROJECT_VERSION}.jar api-gateway.jar

EXPOSE ${SERVER_PORT}

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -Dserver.port=$SERVER_PORT -Dmanagement.server.port=$SERVER_PORT -jar api-gateway.jar"]
