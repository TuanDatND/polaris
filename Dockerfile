# ---------- Build stage ----------
FROM gradle:9.5.1-jdk21 AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY src src

RUN gradle bootJar --no-daemon


# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home appuser

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]