#!/usr/bin/env bash
# Optimize (re-augment) once for SNELLO_DB_KIND, then start the JVM app.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${JAVA_APP_JAR:-/deployments/quarkus-app/quarkus-run.jar}"

if [[ -x "${SCRIPT_DIR}/snello-optimize.sh" ]]; then
  "${SCRIPT_DIR}/snello-optimize.sh" "$JAR"
elif [[ -x /deployments/snello-optimize.sh ]]; then
  /deployments/snello-optimize.sh "$JAR"
fi

if [[ -x /opt/jboss/container/java/run/run-java.sh ]]; then
  exec /opt/jboss/container/java/run/run-java.sh "$@"
fi

exec java ${JAVA_OPTS:-} ${JAVA_OPTS_APPEND:-} -jar "$JAR" "$@"
