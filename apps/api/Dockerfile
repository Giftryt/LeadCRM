# Build from the repository root:  docker build -f apps/api/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY apps/api/pom.xml .
# Download dependencies in their own layer so code changes don't re-download them.
RUN mvn -B -q dependency:go-offline
COPY apps/api/src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/target/lead-tracker-api.jar app.jar
USER app
EXPOSE 4000
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s CMD wget -qO- http://127.0.0.1:4000/api/health || exit 1
# Flyway applies pending migrations on startup.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
