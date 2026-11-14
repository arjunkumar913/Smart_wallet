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
COPY --from=build /app/${MODULE}/target/${MODULE}.jar /tmp/app.jar
COPY deploy/cds-train.sh /tmp/cds-train.sh
# Unpack the jar and build a CDS archive of the classes loaded at startup (see deploy/cds-train.sh): ~3x faster boot
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar \
 && sh /tmp/cds-train.sh && rm /tmp/cds-train.sh
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=75", "-XX:TieredStopAtLevel=1", \
            "-XX:SharedArchiveFile=app.jsa", "-Xlog:cds=off", "-Xlog:cds+dynamic=off", "-jar", "app.jar"]
