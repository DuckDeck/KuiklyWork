@echo off
setlocal
cd /d "%~dp0.."
powershell -ExecutionPolicy Bypass -File "%~dp0sync-ohos-shared.ps1"
exit /b %ERRORLEVEL%