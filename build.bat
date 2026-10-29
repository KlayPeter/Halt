@echo off
rem 编译 debug APK。产物：app\build\outputs\apk\debug\app-debug.apk
setlocal
set "ANDROID_HOME=F:\dev\android-sdk"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "GRADLE_USER_HOME=F:\dev\gradle-home"
call "%~dp0gradlew.bat" :app:assembleDebug --console=plain %*
