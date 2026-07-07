#!/usr/bin/env bash
#
# Vide le pipeline (fonds, millésimes, deals) après un comité d'investissement.
# Les comptes utilisateurs (app_user) et le schéma sont conservés.
#
# ⚠️  ACTION DESTRUCTIVE. Un fichier de sauvegarde restaurable (pipeline-backup-*.sql)
# est écrit automatiquement AVANT la purge ; conservez-le si l'historique doit être gardé.
#
# Usage : scripts/reset-pipeline.sh
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -DskipTests compile >/dev/null
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null
CP="target/classes:$(cat target/cp.txt)"

echo "▸ Current pipeline state:"
java -cp "$CP" com.atlan.mfo.tools.PipelineResetTool
echo

read -r -p "Type RESET in capitals to confirm the permanent purge: " CONFIRM
if [ "$CONFIRM" != "RESET" ]; then
  echo "Cancelled (no changes made)."
  exit 1
fi

java -cp "$CP" com.atlan.mfo.tools.PipelineResetTool --yes
