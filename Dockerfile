# Build Stage
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Optimization for Render Free Tier (512MB RAM)
# -Xmx350m: Max Heap Size 350MB (leaves ~160MB for OS/Metaspace)
# -Xss512k: Thread Stack Size 512KB (Default is 1MB, saving memory per thread)
ENTRYPOINT ["java", "-Xmx350m", "-Xss512k", "-jar", "/app.jar"]
