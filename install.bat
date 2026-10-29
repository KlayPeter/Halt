@echo off
rem Build, install to the connected device, then launch.
setlocal

call "%~dp0scripts\_env.bat"
if errorlevel 1 exit /b 1

set "PKG=com.klaypeter.halt"
set "SVC=%PKG%/%PKG%.guard.GuardService"

echo [1/4] checking device...
adb devices
echo.
echo [2/4] building and installing...
call "%~dp0gradlew.bat" :app:installDebug --console=plain
if errorlevel 1 goto :fail

echo.
echo [3/4] re-binding accessibility service...
adb shell settings --user 0 put secure enabled_accessibility_services none >nul
timeout /t 1 /nobreak >nul
adb shell settings --user 0 put secure enabled_accessibility_services %SVC% >nul
adb shell settings --user 0 put secure accessibility_enabled 1 >nul

echo.
echo [4/4] launching...
adb shell am start -n %PKG%/.MainActivity
echo.
echo Done. If the banner never shows, set battery saver for Halt to "No restrictions".
goto :eof

:fail
echo.
echo Install failed. Usually: device not connected, USB debugging off, screen locked,
echo or "Install via USB" disabled in Developer options.
exit /b 1
