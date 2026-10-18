@echo off
setlocal
title Build Standalone Windows Executable for FIND-MISSING-PEP Edge Agent

echo =====================================================================
echo    Building Standalone Portable Windows .EXE (PyInstaller)
echo =====================================================================
echo.

cd /d "%~dp0"

:: 1. Detect Python
set "PY_CMD="
py -3 --version >nul 2>&1 && set "PY_CMD=py -3"
if not defined PY_CMD (
    python --version >nul 2>&1 && set "PY_CMD=python"
)

if not defined PY_CMD (
    echo [ERROR] Python is not installed or not in PATH!
    pause
    exit /b 1
)

:: 2. Ensure PyInstaller is installed
echo [1/3] Ensuring PyInstaller is installed...
%PY_CMD% -m pip install --upgrade pyinstaller

:: 3. Run PyInstaller
echo.
echo [2/3] Building standalone application package in dist\FindMissingPerson-Agent...
%PY_CMD% -m PyInstaller ^
    --onedir ^
    --windowed ^
    --noconfirm ^
    --name "FindMissingPerson-Agent" ^
    --add-data "edge_agent\ui\resources;ui\resources" ^
    --add-data "edge_agent\config.ini;." ^
    --paths "%~dp0" ^
    edge_agent\main.py

if errorlevel 1 (
    echo.
    echo [ERROR] PyInstaller build failed. Check logs above.
    pause
    exit /b 1
)

:: Copy config.ini into output folder
if exist "edge_agent\config.ini" (
    copy /y "edge_agent\config.ini" "dist\FindMissingPerson-Agent\config.ini" >nul
)

echo.
echo =====================================================================
echo [3/3] BUILD SUCCESSFUL!
echo.
echo Your standalone portable package is in:
echo   dist\FindMissingPerson-Agent\
echo.
echo You can zip that entire folder and copy it to ANY Windows computer!
echo To run it on another PC, simply double-click:
echo   dist\FindMissingPerson-Agent\FindMissingPerson-Agent.exe
echo =====================================================================
echo.
pause
