@echo off
echo Starting Docker Compose with .env.dev...
docker-compose --env-file .env.dev up -d
echo Done.
pause
