@echo off
REM ============================================================
REM Kindergarten Warehouse — Local DEV-profile runner (Windows)
REM ============================================================
REM Đọc env từ .env và chạy với profile dev.
REM ============================================================

if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        set "line=%%A"
        setlocal enabledelayedexpansion
        if not "!line:~0,1!"=="#" if not "!line!"=="" (
            endlocal
            set "%%A=%%B"
        ) else (
            endlocal
        )
    )
) else (
    echo [WARN] Khong tim thay .env — dung default trong application.yml
)

echo Starting Kindergarten Warehouse (DEV Profile)...
mvn spring-boot:run -Dspring-boot.run.profiles=dev
