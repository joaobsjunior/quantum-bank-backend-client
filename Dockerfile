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

# --- Slim runtime image ---
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
