@echo off
title FIND-MISSING-PEP - Unified Launcher
cd /d "%~dp0"

echo =====================================================================
echo           FIND-MISSING-PEP - Launching All Services
echo =====================================================================
echo.

where py >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    py run_all.py %*
) else (
    python run_all.py %*
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Launcher exited with error code %ERRORLEVEL%.
    pause
)
