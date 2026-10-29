@echo off
rem 编译并安装到已连接的手机，然后启动 App
setlocal
set "ANDROID_HOME=F:\dev\android-sdk"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "GRADLE_USER_HOME=F:\dev\gradle-home"
set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
set "PKG=com.klaypeter.halt"
set "SVC=%PKG%/%PKG%.guard.GuardService"

echo [1/4] 检查设备...
adb devices
echo.
echo [2/4] 编译并安装...
call "%~dp0gradlew.bat" :app:installDebug --console=plain
if errorlevel 1 goto :fail

echo.
echo [3/4] 重新绑定无障碍服务...
rem 重装后系统经常不会自动重连，这里强制改一次再改回来触发重绑
adb shell settings --user 0 put secure enabled_accessibility_services none >nul
timeout /t 1 /nobreak >nul
adb shell settings --user 0 put secure enabled_accessibility_services %SVC% >nul
adb shell settings --user 0 put secure accessibility_enabled 1 >nul

echo.
echo [4/4] 启动 App...
adb shell am start -n %PKG%/.MainActivity
echo.
echo 完成。如果横幅不出现，去「设置 - 应用 - 打住 - 省电策略」设成「无限制」。
goto :eof

:fail
echo.
echo 安装失败。常见原因：手机没连上、USB 调试没开、屏幕锁着、或没开开发者选项里的「USB 安装」。
exit /b 1
