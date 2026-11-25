FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN set -eu; \
    for attempt in 1 2 3 4 5; do \
      mvn -B -ntp -Dmaven.wagon.http.retryHandler.count=3 dependency:go-offline && break; \
      if [ "$attempt" -eq 5 ]; then exit 1; fi; \
      case "$attempt" in \
        1) retry_delay=15 ;; \
        2) retry_delay=30 ;; \
        3) retry_delay=60 ;; \
        *) retry_delay=120 ;; \
      esac; \
      echo "Maven Central download failed; retrying dependency resolution in ${retry_delay}s (attempt $((attempt + 1))/5)"; \
      sleep "$retry_delay"; \
    done

COPY src ./src
RUN set -eu; \
    for attempt in 1 2 3 4 5; do \
      mvn -B -ntp -Dmaven.wagon.http.retryHandler.count=3 clean package -DskipTests && break; \
      if [ "$attempt" -eq 5 ]; then exit 1; fi; \
      case "$attempt" in \
        1) retry_delay=15 ;; \
        2) retry_delay=30 ;; \
        3) retry_delay=60 ;; \
        *) retry_delay=120 ;; \
      esac; \
      echo "Maven build dependency download failed; retrying in ${retry_delay}s (attempt $((attempt + 1))/5)"; \
      sleep "$retry_delay"; \
    done

# Share runtime dependencies, health check, and entrypoint across both packaging paths.
FROM eclipse-temurin:21-jre AS runtime-base

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       curl \
       tesseract-ocr \
       tesseract-ocr-eng \
       tesseract-ocr-fra \
       tesseract-ocr-por \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

# CI and staging use the executable JAR produced by CI's Maven verify.
FROM runtime-base AS prebuilt-runtime
COPY target/*.jar app.jar

# Keep this stage last so plain docker build compiles from source without a prebuilt JAR.
FROM runtime-base AS runtime
COPY --from=build /app/target/*.jar app.jar
