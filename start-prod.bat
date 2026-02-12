@echo off
echo Starting Kindergarten Warehouse (PROD Profile)...
echo WARNING: Ensure environment variables are set!
java -jar target/warehouse-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
