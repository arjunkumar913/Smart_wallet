# Cloud build for api-gateway (used by render.yaml). Same as the root Dockerfile, with the module fixed
# and without BuildKit-only features, so any Docker builder can run it.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -q -B -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api-gateway/target/api-gateway.jar app.jar
# Small heap + serial GC keep each service comfortable on a 512 MB free instance
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar"]
