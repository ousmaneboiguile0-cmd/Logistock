#!/usr/bin/env bash
cd "$(dirname "$0")" || exit 1
docker compose down
echo "Application arrêtée (les données sont conservées)."
