@echo off
set "SCRIPT=%~dp0extract_mkv_subtitles.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" %*
echo.
pause
