# syntax=docker/dockerfile:1.7
# Backend LMS (Spring Boot 4, Java 17) — image multi-étapes : compilation Maven puis JRE seul.

# ── 1. Compilation ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
# Dépendances d'abord : couche mise en cache tant que pom.xml ne change pas
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ── 2. Exécution ────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system lms && useradd --system --gid lms --home-dir /app lms
WORKDIR /app
# Couches Spring Boot : seules les classes de l'application changent d'une version à l'autre
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
# Fichiers des cours et photos de profil (volume persistant en production)
RUN mkdir -p /app/uploads/videos /app/uploads/avatars && chown -R lms:lms /app/uploads
USER lms

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8090 \
    MANAGEMENT_PORT=8091
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS "http://localhost:${MANAGEMENT_PORT}/actuator/health/liveness" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
