#!/usr/bin/env bash
#
# Vide le pipeline (fonds, millésimes, deals) après un comité d'investissement.
# Les comptes utilisateurs (app_user) et le schéma sont conservés.
#
# ⚠️  ACTION DESTRUCTIVE ET IRRÉVERSIBLE. Aucune sauvegarde automatique n'est
# faite par ce script — faire un pg_dump avant si l'historique doit être gardé.
#
# Usage : scripts/reset-pipeline.sh
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -DskipTests compile >/dev/null
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes:$(cat target/cp.txt)"

echo "▸ État actuel du pipeline :"
java -cp "$CP" com.atlan.mfo.tools.PipelineResetTool
echo

read -r -p "Taper RESET en majuscules pour confirmer la purge définitive : " CONFIRM
if [ "$CONFIRM" != "RESET" ]; then
  echo "Annulé (aucune modification)."
  exit 1
fi

java -cp "$CP" com.atlan.mfo.tools.PipelineResetTool --yes
