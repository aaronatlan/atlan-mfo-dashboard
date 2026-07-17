#!/usr/bin/env bash
#
# Import ponctuel en masse d'un pipeline existant (fonds + millésimes + deals)
# depuis un classeur Excel — voir templates/pipeline-import-template.xlsx pour
# le modèle de colonnes attendu, et DEPLOYMENT.md pour le mode d'emploi complet.
#
# Usage :
#   scripts/import-pipeline.sh <fichier.xlsx> <identifiant_utilisateur>
#
# La connexion base est lue depuis config.properties (ou les variables ATLAN_DB_*)
# du répertoire courant, comme l'application. Rien n'est écrit en base tant que
# le fichier entier n'est pas valide (voir la sortie en cas d'erreur).
set -euo pipefail

cd "$(dirname "$0")/.."

if [ "$#" -lt 2 ]; then
  echo "Usage: scripts/import-pipeline.sh <fichier.xlsx> <identifiant_utilisateur>" >&2
  exit 2
fi

mvn -q -DskipTests compile >/dev/null
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null

java -cp "target/classes:$(cat target/cp.txt)" com.atlan.mfo.tools.PipelineImportTool "$@"
