#!/usr/bin/env bash
#
# Provisionne (ou réinitialise) un utilisateur applicatif — voir DEPLOYMENT.md.
#
# Usage :
#   scripts/user-add.sh <username> <mot_de_passe_temporaire> "<Nom complet>" [ANALYST|PARTNER]
#
# La connexion base est lue depuis config.properties (ou les variables ATLAN_DB_*)
# du répertoire courant, comme l'application. Le compte devra changer son mot de
# passe au premier login.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ "$#" -lt 3 ]; then
  echo "Usage: scripts/user-add.sh <username> <password> \"<Full name>\" [ANALYST|PARTNER]" >&2
  exit 2
fi

mvn -q -DskipTests compile >/dev/null
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt >/dev/null

java -cp "target/classes:$(cat target/cp.txt)" com.atlan.mfo.tools.AdminTool "$@"
