#!/usr/bin/env bash
# Keycloak-style Quarkus re-augmentation for Snello DB-facing apps.
# Specializes the mutable-jar to a single quarkus.datasource.db-kind, then exits.
#
# Usage:
#   SNELLO_DB_KIND=postgresql ./snello-optimize.sh /deployments/quarkus-app/quarkus-run.jar
#   SNELLO_DB_KIND=mysql ./snello-optimize.sh
#   SNELLO_DB_KIND=h2 ./snello-optimize.sh
set -euo pipefail

JAR="${1:-${JAVA_APP_JAR:-/deployments/quarkus-app/quarkus-run.jar}}"
DB_KIND="${SNELLO_DB_KIND:-${QUARKUS_DATASOURCE_DB_KIND:-postgresql}}"
MARKER_DIR="$(dirname "$JAR")"
MARKER="${SNELLO_OPTIMIZE_MARKER:-$MARKER_DIR/.snello-optimized-${DB_KIND}}"

if [[ "${SNELLO_SKIP_OPTIMIZE:-false}" == "true" ]]; then
  echo "snello-optimize: skipped (SNELLO_SKIP_OPTIMIZE=true)"
  exit 0
fi

if [[ -f "$MARKER" ]]; then
  echo "snello-optimize: already optimized for db-kind=${DB_KIND} ($MARKER)"
  exit 0
fi

echo "snello-optimize: re-augmenting for db-kind=${DB_KIND} ..."
java ${JAVA_OPTS:-} ${JAVA_OPTS_APPEND:-} \
  -Dquarkus.launch.rebuild=true \
  -Dquarkus.datasource.db-kind="${DB_KIND}" \
  -Dsnello.dbtype="${DB_KIND}" \
  -jar "$JAR"

mkdir -p "$(dirname "$MARKER")"
echo "${DB_KIND}" > "$MARKER"
echo "snello-optimize: done"
