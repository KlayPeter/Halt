@echo off
rem Common environment check: Android SDK / JDK / adb
rem Priority: ANDROID_HOME -> sdk.dir in local.properties -> default install path
rem NOTE: no ( ) blocks here on purpose, to avoid cmd delayed-expansion pitfalls.

if defined ANDROID_HOME goto :check

set "SDK_FROM_PROPS="
if exist "%~dp0..\local.properties" for /f "tokens=1,* delims==" %%A in ('findstr /b /c:"sdk.dir" "%~dp0..\local.properties" 2^>nul') do set "SDK_FROM_PROPS=%%B"
if not defined SDK_FROM_PROPS goto :try_default
set "ANDROID_HOME=%SDK_FROM_PROPS:/=\%"
if defined ANDROID_HOME goto :check

:try_default
if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if defined ANDROID_HOME goto :check

echo [x] Android SDK not found.
echo     1) Install Android Studio (bundles the SDK), or run:
echo          setx ANDROID_HOME "D:\Android\Sdk"
echo     2) Or create local.properties in the repo root with:
echo          sdk.dir=D:/Android/Sdk
exit /b 1

:check
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" goto :no_platform_tools
if not defined JAVA_HOME if exist "%ProgramFiles%\Java\jdk-21" set "JAVA_HOME=%ProgramFiles%\Java\jdk-21"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JAVA_HOME echo [!] JAVA_HOME not set, using java from PATH (need JDK 17+).

set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
echo SDK : %ANDROID_HOME%
if defined JAVA_HOME echo JDK : %JAVA_HOME%
echo.
exit /b 0

:no_platform_tools
echo [x] platform-tools missing under:
echo      %ANDROID_HOME%
echo     Install "Android SDK Platform-Tools" via SDK Manager.
exit /b 1
