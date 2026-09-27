# syntax=docker/dockerfile:1
# Contexto de build: raíz del repo (el jar incluye specs/api/openapi.yaml, ADR-0005).
#   docker build -f backend/Dockerfile .

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY specs/api ./specs/api
COPY backend/.mvn ./backend/.mvn
COPY backend/mvnw backend/pom.xml ./backend/
WORKDIR /workspace/backend
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q dependency:go-offline
COPY backend/src ./src
# Las pruebas (Testcontainers) corren en CI, no dentro del build de la imagen.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -DskipTests package \
    && java -Djarmode=tools -jar target/polla-*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/backend/target/extracted/dependencies/ ./
COPY --from=build /workspace/backend/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/backend/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/backend/target/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
