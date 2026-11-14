# Cloud build for wallet-service (used by render.yaml). Same as the root Dockerfile, with the module fixed
# and without BuildKit-only features, so any Docker builder can run it.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -q -B -pl wallet-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/wallet-service/target/wallet-service.jar /tmp/app.jar
COPY deploy/cds-train.sh /tmp/cds-train.sh

# Fast cold starts on a small free instance (the service sleeps when idle and boots again on the next request):
# 1. Unpack the jar so classes load straight from lib/ instead of from nested jars.
# 2. Start the app once here, at build time, and save the loaded classes to a CDS archive (app.jsa).
#    At runtime the JVM maps that archive instead of reading, parsing and verifying ~15,000 classes again.
#    Measured on one CPU core: about 3x faster startup (18 s -> 6 s).
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar \
 && sh /tmp/cds-train.sh && rm /tmp/cds-train.sh

# -XX:TieredStopAtLevel=1: only the quick C1 compiler. Halves startup time on a fraction of a CPU;
#   peak throughput is lower than with C2, which does not matter at this traffic.
# Small heap + serial GC keep each service comfortable on a 512 MB free instance.
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-Xss512k", \
            "-XX:TieredStopAtLevel=1", "-XX:SharedArchiveFile=app.jsa", "-Xlog:cds=off", "-Xlog:cds+dynamic=off", \
            "-jar", "app.jar"]
