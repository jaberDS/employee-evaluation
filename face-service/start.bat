@echo off
REM Demarrage du service de reconnaissance faciale ATB.
REM
REM Le premier lancement telecharge le modele buffalo_l (~300 Mo) dans
REM %USERPROFILE%\.insightface\models — comptez quelques minutes.
REM
REM Le service n'ecoute que sur 127.0.0.1 : seul Spring doit l'appeler,
REM il n'a aucune authentification propre.

cd /d "%~dp0"

echo [ATB] Demarrage du service facial sur http://127.0.0.1:8000 ...
python -m uvicorn main:app --host 127.0.0.1 --port 8000
