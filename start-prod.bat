@echo off
REM ============================================================
REM Kindergarten Warehouse — Local PROD-profile runner (Windows)
REM ============================================================
REM Chạy app với profile prod, đọc env từ .env.prod.
REM Lưu ý: file này dành để chạy cục bộ. Trên VPS thật hãy dùng
REM docker-compose + scripts/deploy.sh (xem DEPLOYMENT_GUIDE.md).
REM ============================================================

if not exist ".env.prod" (
    echo [ERROR] Khong tim thay .env.prod. Hay copy tu .env.example va dien gia tri.
    exit /b 1
)

REM Load .env.prod vao environment (Windows cmd)
for /f "usebackq tokens=1,* delims==" %%A in (".env.prod") do (
    set "line=%%A"
    setlocal enabledelayedexpansion
    if not "!line:~0,1!"=="#" if not "!line!"=="" (
        endlocal
        set "%%A=%%B"
    ) else (
        endlocal
    )
)

if not exist "target\warehouse-0.0.1-SNAPSHOT.jar" (
    echo [INFO] JAR chua co, build truoc...
    call mvn -q -DskipTests package || exit /b 1
)

echo Starting Kindergarten Warehouse (PROD Profile)...
java -jar target\warehouse-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
