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

# Java 17 is container-aware. We can control memory via Docker limits or JAVA_TOOL_OPTIONS if needed.
ENTRYPOINT ["java", "-jar", "app.jar"]
