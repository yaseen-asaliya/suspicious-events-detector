FROM eclipse-temurin:8-jre-jammy
# eclipse-temurin is actively maintained; openjdk:8-jre-slim is EOL on Docker Hub.

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r spring && useradd -r -g spring spring

WORKDIR /app
COPY --chown=spring:spring target/*.jar app.jar

USER spring
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health/is-ready || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
