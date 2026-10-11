# Used after Maven package has already run locally/in the Maven container.
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends libreoffice-writer libreoffice-calc libreoffice-impress fonts-dejavu-core fonts-liberation2 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/warehouse-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
