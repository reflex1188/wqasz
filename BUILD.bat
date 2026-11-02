@echo off
setlocal
cd /d "%~dp0"
title SkyLyrics build

echo Building SkyLyrics. The first run downloads Minecraft and takes a few minutes.
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found on this PC.
  echo Install JDK 21 or newer, then double-click this file again.
  echo Opening the download page...
  start "" "https://adoptium.net/temurin/releases/?version=21"
  echo.
  pause
  exit /b 1
)

call gradlew.bat build
if errorlevel 1 (
  echo.
  echo The build failed. Make sure you have JDK 21 or newer and an internet connection.
  echo Copy the error text above and send it to Jax.
  echo.
  pause
  exit /b 1
)

if not exist "JAR-READY" mkdir "JAR-READY"
copy /Y "build\libs\skylyrics-1.0.0.jar" "JAR-READY\skylyrics-1.0.0.jar" >nul
if errorlevel 1 (
  echo.
  echo Build finished but the jar was not found in build\libs.
  pause
  exit /b 1
)

echo.
echo DONE. Drag JAR-READY\skylyrics-1.0.0.jar into your Fabric 1.21.4 mods folder
echo or your launcher's mod profile. You also need Fabric API for 1.21.4.
start "" explorer "JAR-READY"
echo.
pause
