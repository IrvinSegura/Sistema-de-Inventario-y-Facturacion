# syntax=docker/dockerfile:1

# Build stage: the Maven wrapper pins the same Maven as local builds and CI.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

# Dependencies first, so source changes reuse this layer (and the cache mount).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI (they need Docker for Testcontainers); the image only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# Runtime stage: JRE only, non-root.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

# Least to most frequently changed, so redeploys only push the application layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# Heap sized from the container's memory limit, leaving room for metaspace and threads (the app
# idles at ~340 MB in 512 MB); crash (and get restarted) instead of limping on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
