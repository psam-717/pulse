# ---- Build stage: compile the Spring Boot fat jar ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies first (only re-resolves when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B \
    && cp "$(ls target/*.jar | grep -v original)" /app.jar

# ---- Runtime stage: minimal JRE image, non-root ----
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app.jar app.jar

# Heap tuned for Render free/starter tiers (512 MB RAM). Bump for bigger plans.
ENV JAVA_OPTS="-Xmx300m -XX:MaxMetaspaceSize=180m -XX:+UseContainerSupport"

# Render injects the listening port via $PORT (server.port=${PORT:8080} in properties)
EXPOSE 8080

RUN useradd -r -u 10001 appuser && chown -R appuser:appuser /app
USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
