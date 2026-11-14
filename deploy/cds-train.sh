#!/bin/sh
# Builds the CDS archive (app.jsa) during the Docker build.
# Runs the app until its Spring context is ready, then exits (spring.context.exit=onRefresh), recording every class
# it loaded. There is no database or RabbitMQ at build time, so everything that would connect is switched off:
# nothing here talks to the network, and listeners, schedulers and Liquibase never start.
# If training fails, the image still works: the JVM simply starts without the archive (just slower).
cd /app
java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:1/cds-training \
  -Dspring.liquibase.enabled=false \
  -Dspring.jpa.hibernate.ddl-auto=none \
  -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
  -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
  -Dspring.rabbitmq.host=127.0.0.1 -Dspring.rabbitmq.port=1 \
  -Dspring.rabbitmq.listener.simple.auto-startup=false \
  -jar app.jar > /tmp/cds-train.log 2>&1 \
  || { echo "CDS training failed; the service will start without the archive"; tail -n 20 /tmp/cds-train.log; rm -f app.jsa; }
[ -f app.jsa ] && echo "CDS archive ready: $(du -h app.jsa | cut -f1)"
exit 0
