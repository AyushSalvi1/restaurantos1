# syntax=docker/dockerfile:1
# Build the Spring Boot jar in a full JDK stage, then ship it on a JRE-only runtime image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies against the POM alone so the layer is reused whenever only sources change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime

# Never run the service as root: a container escape should not start as uid 0.
RUN groupadd --system lifeos && useradd --system --gid lifeos --home /app lifeos

# curl is installed explicitly rather than assumed. The healthcheck below depends on it, and a base image
# that quietly lacks it turns every container unhealthy, which blocks `depends_on: service_healthy` for
# the frontend and leaves the stack permanently half-started.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /build/target/lifeos-backend-*.jar /app/app.jar
RUN mkdir -p /app/data/storage /app/data/knowledge /app/data/exports \
    && chown -R lifeos:lifeos /app

USER lifeos

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=5 \
    CMD ["sh", "-c", "curl -fsS http://127.0.0.1:8080/actuator/health/readiness | grep -q UP"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
