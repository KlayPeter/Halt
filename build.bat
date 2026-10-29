@echo off
rem Build debug APK -> app\build\outputs\apk\debug\app-debug.apk
setlocal

call "%~dp0scripts\_env.bat"
if errorlevel 1 exit /b 1

call "%~dp0gradlew.bat" :app:assembleDebug --console=plain %*
