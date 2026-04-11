# Stage 1: Build React frontend
# --platform=$BUILDPLATFORM ensures this always runs natively on the build host,
# avoiding slow QEMU emulation for a platform-independent JS build.
FROM --platform=$BUILDPLATFORM node:20-alpine AS frontend-build
WORKDIR /app
COPY frontend/package*.json frontend/
RUN cd frontend && npm ci
COPY frontend/ frontend/
RUN cd frontend && npm run build
# Output: /app/src/main/resources/static/

# Stage 2: Build Spring Boot JAR
# --platform=$BUILDPLATFORM keeps compilation native on the build host.
# The resulting JAR is platform-independent bytecode.
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk-alpine AS backend-build
WORKDIR /app
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/ gradle/
COPY src/ src/
COPY --from=frontend-build /app/src/main/resources/static/ src/main/resources/static/
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# Stage 3: Runtime — target platform image (amd64 / arm64 / arm/v7)
# eclipse-temurin:17-jre-jammy (Ubuntu Jammy) provides multi-arch support
# including linux/amd64, linux/arm64, and linux/arm/v7 (Raspberry Pi 3).
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
RUN mkdir -p /app/data
COPY --from=backend-build /app/build/libs/*.jar app.jar
# JVM tuning for low-memory environments such as Raspberry Pi 3 (512 MB heap limit).
ENV JAVA_TOOL_OPTIONS="-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
