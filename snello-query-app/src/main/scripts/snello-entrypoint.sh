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

# Query plane: make every pooled connection read-only at the DB session level (defense in depth on top of
# the build-time removal of write endpoints). Opt out with SNELLO_READ_ONLY_SESSION=false, or override by
# setting QUARKUS_DATASOURCE_JDBC_NEW_CONNECTION_SQL yourself.
if [[ "${SNELLO_READ_ONLY_SESSION:-true}" == "true" && -z "${QUARKUS_DATASOURCE_JDBC_NEW_CONNECTION_SQL:-}" ]]; then
  case "${SNELLO_DB_KIND:-postgresql}" in
    postgresql) export QUARKUS_DATASOURCE_JDBC_NEW_CONNECTION_SQL="SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY" ;;
    mysql|mariadb) export QUARKUS_DATASOURCE_JDBC_NEW_CONNECTION_SQL="SET SESSION TRANSACTION READ ONLY" ;;
    *) echo "snello-entrypoint: no read-only session SQL for db-kind=${SNELLO_DB_KIND}" ;;
  esac
  echo "snello-entrypoint: read-only session sql='${QUARKUS_DATASOURCE_JDBC_NEW_CONNECTION_SQL:-}'"
fi

if [[ -x /opt/jboss/container/java/run/run-java.sh ]]; then
  exec /opt/jboss/container/java/run/run-java.sh "$@"
fi

exec java ${JAVA_OPTS:-} ${JAVA_OPTS_APPEND:-} -jar "$JAR" "$@"
