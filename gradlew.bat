@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo This MVP bootstrap expects Gradle 9.4.1 on Windows. Install Gradle or generate the official wrapper from Android Studio.
  exit /b 1
)
gradle %*
