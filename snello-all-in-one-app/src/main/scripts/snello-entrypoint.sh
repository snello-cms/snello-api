#!/usr/bin/env bash
# Optimize (re-augment) once for SNELLO_DB_KIND, then start the JVM app.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${JAVA_APP_JAR:-/deployments/quarkus-app/quarkus-run.jar}"
DB_KIND="${SNELLO_DB_KIND:-${QUARKUS_DATASOURCE_DB_KIND:-h2}}"
JDBC_URL="${SNELLO_JDBC_URL:-${QUARKUS_DATASOURCE_JDBC_URL:-}}"
# a password inside the URL (password=... parameter) never reaches the log
SHOWN_URL=$(sed -E 's/([?&;]password=)[^&;]*/\1***/I' <<<"${JDBC_URL:-<default H2 file /data/snello>}")
echo "snello-entrypoint: db-kind=${DB_KIND} jdbc-url=${SHOWN_URL}"

# an external database needs its URL: without it the H2 file default would be handed to that driver
if [[ "$DB_KIND" != "h2" && -z "$JDBC_URL" ]]; then
  echo "snello-entrypoint: ERROR db-kind=${DB_KIND} needs SNELLO_JDBC_URL (plus SNELLO_DB_USERNAME / SNELLO_DB_PASSWORD), e.g." >&2
  case "$DB_KIND" in
    postgresql) echo "  SNELLO_JDBC_URL=jdbc:postgresql://db-host:5432/snello" >&2 ;;
    mysql)      echo "  SNELLO_JDBC_URL=jdbc:mysql://db-host:3306/snello" >&2 ;;
    mssql)      echo "  SNELLO_JDBC_URL='jdbc:sqlserver://db-host:1433;databaseName=snello;encrypt=true;trustServerCertificate=false'" >&2 ;;
  esac
  exit 1
fi
if [[ "$DB_KIND" != "h2" && "$JDBC_URL" == jdbc:h2:* ]]; then
  echo "snello-entrypoint: ERROR db-kind=${DB_KIND} with an H2 URL (${JDBC_URL}): use the image for H2 or a ${DB_KIND} URL" >&2
  exit 1
fi

if [[ -x "${SCRIPT_DIR}/snello-optimize.sh" ]]; then
  "${SCRIPT_DIR}/snello-optimize.sh" "$JAR"
elif [[ -x /deployments/snello-optimize.sh ]]; then
  /deployments/snello-optimize.sh "$JAR"
fi

if [[ -x /opt/jboss/container/java/run/run-java.sh ]]; then
  exec /opt/jboss/container/java/run/run-java.sh "$@"
fi

exec java ${JAVA_OPTS:-} ${JAVA_OPTS_APPEND:-} -jar "$JAR" "$@"
