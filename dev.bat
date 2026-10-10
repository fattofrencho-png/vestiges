@echo off
rem Runs Gradle with the JDK 25 that Minecraft 26.3 needs.
rem   dev build | dev test | dev runClient | dev runServer
rem   dev deploy     build, then replace the jar in your game instance's mods folder
rem                  (set VESTIGES_MODS to override; default is the modtesting instance in ATLauncher)
rem Looks for a JDK 25 in %USERPROFILE%\.jdks first, then falls back to whatever JAVA_HOME already is.
cd /d "%~dp0"
for /d %%D in ("%USERPROFILE%\.jdks\jdk-25*") do set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME (echo No JDK 25 found in %USERPROFILE%\.jdks. Install Temurin 25 and try again.& exit /b 1)
if /i "%~1"=="deploy" goto deploy
call "%~dp0gradlew.bat" %*
exit /b %errorlevel%

:deploy
call "%~dp0gradlew.bat" build
if errorlevel 1 exit /b 1
if not defined VESTIGES_MODS set "VESTIGES_MODS=%APPDATA%\ATLauncher\instances\modtesting\mods"
if not exist "%VESTIGES_MODS%" (echo Mods folder not found: %VESTIGES_MODS%& exit /b 1)
del /q "%VESTIGES_MODS%\vestiges-*.jar" 2>nul
for %%F in (build\libs\vestiges-*.jar) do (
  echo %%~nF | findstr /c:"sources" >nul || copy /y "%%F" "%VESTIGES_MODS%\" >nul
)
echo Deployed to %VESTIGES_MODS%
dir /b "%VESTIGES_MODS%\vestiges-*.jar"
