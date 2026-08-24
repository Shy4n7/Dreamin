@echo off
setlocal
echo ===================================================
echo [Dreamin] Building and Running Ultra-Modern UI...
echo ===================================================

set "JAVA_HOME=C:\Users\shyan\.jdks\jbr-21.0.11"
set "PATH=%PATH%;C:\Users\shyan\AppData\Local\Android\Sdk\platform-tools;C:\Users\shyan\.jdks\jbr-21.0.11\bin"

echo 1. Installing Debug APK...
call gradlew.bat installDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build or installation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo 2. Setting up ADB port forwarding...
adb reverse tcp:8080 tcp:8080 >nul 2>&1

echo 3. Launching Dreamin App...
adb shell am start -n com.shyan.dreamin/.MainActivity

echo ===================================================
echo [SUCCESS] Dreamin is now running on your device!
echo ===================================================
endlocal
