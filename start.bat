@echo off
cd /d "%~dp0"
echo ============================================
echo   LogiStock SA : demarrage de l'application
echo ============================================
echo.

docker --version >nul 2>&1
if errorlevel 1 (
  echo [ERREUR] Docker n'est pas installe.
  echo Installez Docker Desktop sur https://www.docker.com puis relancez ce fichier.
  pause
  exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
  echo [ERREUR] Docker Desktop n'est pas demarre.
  echo Lancez Docker Desktop, attendez qu'il soit pret, puis relancez ce fichier.
  pause
  exit /b 1
)

echo Premier lancement : 5 a 10 minutes et environ 1 Go de donnees. Patientez...
echo.
docker compose up -d --build
if errorlevel 1 (
  echo.
  echo [ERREUR] Le demarrage a echoue. Copiez les dernieres lignes ci-dessus et envoyez-les a l'equipe.
  pause
  exit /b 1
)

echo.
echo Attente du demarrage de l'API...
set /a n=0
:attente
set /a n+=1
curl -s -o nul -w "%%{http_code}" http://localhost:8081/api/auth/me | findstr "401" >nul
if not errorlevel 1 goto pret
if %n% geq 40 goto tard
timeout /t 3 /nobreak >nul
goto attente

:tard
echo L'API met du temps a demarrer. Ouverture du site quand meme.
echo En cas de probleme : docker compose logs backend

:pret
echo.
echo Application prete : http://localhost:8081
echo Comptes de test (mot de passe Passw0rd!) : admin@logistock.test, stock@logistock.test, vente@logistock.test
start http://localhost:8081
echo.
pause
