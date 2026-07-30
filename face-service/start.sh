#!/usr/bin/env sh
# Démarrage du service de reconnaissance faciale ATB.
#
# Le premier lancement télécharge le modèle buffalo_l (~300 Mo) dans
# ~/.insightface/models — comptez quelques minutes.
#
# Le service n'écoute que sur 127.0.0.1 : seul Spring doit l'appeler,
# il n'a aucune authentification propre.

set -e
cd "$(dirname "$0")"

echo "[ATB] Démarrage du service facial sur http://127.0.0.1:8000 ..."
exec python -m uvicorn main:app --host 127.0.0.1 --port 8000
