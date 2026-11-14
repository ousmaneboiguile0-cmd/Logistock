@echo off
cd /d "%~dp0"
docker compose down
echo.
echo Application arretee (les donnees sont conservees).
pause
