#!/usr/bin/env bash
#
# Construit un paquet natif de l'application via jpackage (voir §13.5).
#
# Usage : scripts/package.sh [type]
#   type = app-image (défaut, portable) | dmg | pkg (macOS)
#                          | msi | exe (Windows) | deb | rpm (Linux)
#
# jpackage embarque un runtime Java réduit (jlink) : aucun JDK requis sur le poste cible.
set -euo pipefail

cd "$(dirname "$0")/.."

APP_NAME="Atlan MFO Dashboard"
JAR_VERSION="0.1.0"                 # version du projet Maven (nom du jar)
PKG_VERSION="1.0.0"                 # version du paquet natif (macOS exige un 1er nombre ≥ 1)
MAIN_JAR="atlan-mfo-dashboard-${JAR_VERSION}.jar"
MAIN_CLASS="com.atlan.mfo.Launcher"
TYPE="${1:-app-image}"

echo "▸ Build Maven (jar + dépendances)…"
mvn -q -DskipTests clean package

# L'entrée jpackage = le jar principal + les dépendances (déjà dans dist-input/lib)
cp "target/${MAIN_JAR}" "target/dist-input/"

# macOS : purge les attributs étendus (FinderInfo iCloud) qui font échouer la signature ad-hoc
if [ "$(uname)" = "Darwin" ]; then
  xattr -cr target/dist-input 2>/dev/null || true
fi

echo "▸ jpackage (type=${TYPE})…"
rm -rf target/installer

# Signature macOS optionnelle : exporter MAC_SIGN_IDENTITY="Developer ID Application: … (TEAMID)"
# pour signer avec un certificat Apple (préalable à la notarisation). Sinon signature ad-hoc.
EXTRA_ARGS=()
if [ "$(uname)" = "Darwin" ] && [ -n "${MAC_SIGN_IDENTITY:-}" ]; then
  EXTRA_ARGS+=(--mac-sign --mac-signing-key-user-name "${MAC_SIGN_IDENTITY}")
  echo "  (signature avec l'identité : ${MAC_SIGN_IDENTITY})"
fi

jpackage \
  --type "${TYPE}" \
  --name "${APP_NAME}" \
  --app-version "${PKG_VERSION}" \
  --vendor "Atlan MFO" \
  --input target/dist-input \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --java-options "-Dfile.encoding=UTF-8" \
  --dest target/installer \
  ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}

echo "▸ Terminé →"
ls -la target/installer
