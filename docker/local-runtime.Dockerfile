# Used after Maven package has already run locally/in the Maven container.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/warehouse-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
