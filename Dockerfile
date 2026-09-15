# syntax=docker/dockerfile:1

# --- Build + test + 100% coverage gate (no host toolchain needed) ---
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /home/gradle/project

# Warm the dependency cache on the build files alone for better layer caching.
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies >/dev/null 2>&1 || true

# Compile, run the tests, and enforce the Kover 100% line-coverage gate.
COPY --chown=gradle:gradle src ./src
RUN gradle --no-daemon check bootJar

# --- Slim runtime image (non-root) ---
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

RUN groupadd --system --gid 10001 quantumbank \
    && useradd --system --uid 10001 --gid quantumbank --home-dir /app --shell /usr/sbin/nologin quantumbank

COPY --from=build --chown=quantumbank:quantumbank /home/gradle/project/build/libs/*.jar app.jar

USER quantumbank
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
