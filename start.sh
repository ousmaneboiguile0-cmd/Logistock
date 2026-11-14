#!/usr/bin/env bash
# Lance LogiStock SA (Mac / Linux) : ./start.sh
cd "$(dirname "$0")" || exit 1
echo "=== LogiStock SA : démarrage ==="

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERREUR] Docker n'est pas installé : https://www.docker.com"; exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "[ERREUR] Docker n'est pas démarré. Lancez-le puis relancez ce script."; exit 1
fi

echo "Premier lancement : 5 à 10 minutes et environ 1 Go de données. Patientez..."
docker compose up -d --build || { echo "[ERREUR] Le démarrage a échoué (voir ci-dessus)."; exit 1; }

echo "Attente du démarrage de l'API..."
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/auth/me)
  [ "$code" = "401" ] && break
  sleep 3
done

echo
echo "Application prête : http://localhost:8081"
echo "Comptes de test (mot de passe Passw0rd!) : admin@logistock.test, stock@logistock.test, vente@logistock.test"
(command -v open >/dev/null && open http://localhost:8081) || (command -v xdg-open >/dev/null && xdg-open http://localhost:8081) || true
