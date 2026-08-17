FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN set -eu; \
    for attempt in 1 2 3 4; do \
      mvn -B -ntp -Dmaven.wagon.http.retryHandler.count=3 dependency:go-offline && break; \
      if [ "$attempt" -eq 4 ]; then exit 1; fi; \
      echo "Maven Central download failed; retrying dependency resolution (attempt $((attempt + 1))/4)"; \
      sleep $((attempt * 5)); \
    done

COPY src ./src
RUN set -eu; \
    for attempt in 1 2 3; do \
      mvn -B -ntp -Dmaven.wagon.http.retryHandler.count=3 clean package -DskipTests && break; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      echo "Maven build dependency download failed; retrying (attempt $((attempt + 1))/3)"; \
      sleep $((attempt * 5)); \
    done

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
