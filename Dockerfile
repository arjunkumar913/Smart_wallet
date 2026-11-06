# syntax=docker/dockerfile:1
# Shared multi-stage Dockerfile for every service.
# Usage: docker build --build-arg MODULE=auth-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /app
COPY . .
# The cache mount keeps ~/.m2 between builds, so libraries are downloaded once and shared by all services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /app/${MODULE}/target/${MODULE}.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
