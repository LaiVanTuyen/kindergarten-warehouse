@echo off
echo Starting Docker Compose with .env.prod...
docker-compose --env-file .env.prod up -d
echo Done.
pause
