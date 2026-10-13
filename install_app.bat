@echo off
setlocal
echo ========================================================
echo   FIND-MISSING-PEP: Android App Installer
echo ========================================================
echo.

set ADB_PATH=C:\Users\mksin\AppData\Local\Android\Sdk\platform-tools\adb.exe

if not exist "%ADB_PATH%" (
    set ADB_PATH=adb
)

echo Checking connected Android devices...
"%ADB_PATH%" devices
echo.

echo Installing FIND-MISSING-PEP.apk onto connected device...
"%ADB_PATH%" install -r "%~dp0FIND-MISSING-PEP.apk"

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] App installed successfully!
    echo Launching FIND-MISSING-PEP on phone...
    "%ADB_PATH%" shell am start -n com.findmissingpep.app/.presentation.MainActivity
) else (
    echo.
    echo [NOTE] If no device was detected:
    echo 1. Connect your Android phone with a USB cable.
    echo 2. Enable USB Debugging in Developer Options on your phone.
    echo 3. Run this script again.
)

echo.
pause
