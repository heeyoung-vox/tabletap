# Build stage: compiles the app with the Gradle wrapper. Tests run in CI, not here.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

# Runtime stage: only the JRE and the jar, running as an unprivileged user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 tabletap
WORKDIR /app
COPY --from=build /src/build/libs/tabletap.jar app.jar
USER tabletap
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
