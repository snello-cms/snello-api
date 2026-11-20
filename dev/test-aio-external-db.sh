#!/usr/bin/env bash
# All-in-one on external databases, with docker compose (docker-compose/compose-allinone-external-db.yml): for each of
# postgresql, mysql, mssql builds the image, starts DB + snello, runs dev/scripts/aio-db-smoke.py (table DDL + CRUD +
# search as Admin), restarts snello, checks the data survived, then removes the stack and its volumes.
# Needs the local dev Keycloak (http://localhost:8180, realm snello) for the Admin token.
# Usage: dev/test-aio-external-db.sh [--no-package] [postgresql mysql mssql]   KEEP=1 keeps the stacks running.
set -uo pipefail
cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f docker-compose/compose-allinone-external-db.yml)
if [[ "${1:-}" == "--no-package" ]]; then shift; else
  echo "=== mvn package snello-all-in-one-app"
  ./mvnw -o -q -pl snello-all-in-one-app -am package -DskipTests || { echo "package failed"; exit 1; }
fi
DBS=("${@:-postgresql mysql mssql}"); read -r -a DBS <<<"${DBS[*]}"
declare -A PORT=([postgresql]=18081 [mysql]=18082 [mssql]=18083)
results=()

for db in "${DBS[@]}"; do
  echo; echo "=== $db"
  url="http://localhost:${PORT[$db]}"
  "${COMPOSE[@]}" --profile "$db" down -v >/dev/null 2>&1
  if ! "${COMPOSE[@]}" --profile "$db" up -d --build >"target/aio-$db-up.log" 2>&1; then
    results+=("$db FAILED: compose up (target/aio-$db-up.log)"); continue
  fi
  up=false
  for _ in $(seq 1 60); do
    [[ "$(curl -s -o /dev/null -m 3 -w '%{http_code}' "$url/api/metadatas")" == 200 ]] && { up=true; break; }
    sleep 3
  done
  "${COMPOSE[@]}" --profile "$db" logs "aio-$db" >"target/aio-$db.log" 2>&1
  grep -E 'snello-entrypoint|snello-optimize|dbtype:|started in' "target/aio-$db.log" | sed 's/^/  /' | cut -c1-200
  if ! $up; then results+=("$db FAILED: not up (target/aio-$db.log)"); continue; fi
  if SNELLO_URL="$url" python3 -I dev/scripts/aio-db-smoke.py write \
     && "${COMPOSE[@]}" --profile "$db" restart "aio-$db" >/dev/null 2>&1 \
     && for _ in $(seq 1 40); do [[ "$(curl -s -o /dev/null -m 3 -w '%{http_code}' "$url/api/metadatas")" == 200 ]] && break; sleep 3; done \
     && SNELLO_URL="$url" python3 -I dev/scripts/aio-db-smoke.py read; then
    errs=$("${COMPOSE[@]}" --profile "$db" logs "aio-$db" 2>&1 | grep -cE 'SQLException|SQLServerException|schema upgrade failed|BatchUpdateException')
    results+=("$db OK (SQL errors in log: $errs)")
  else
    results+=("$db FAILED (target/aio-$db.log)")
  fi
  "${COMPOSE[@]}" --profile "$db" logs "aio-$db" >"target/aio-$db.log" 2>&1
  [[ "${KEEP:-0}" == 1 ]] || "${COMPOSE[@]}" --profile "$db" down -v >/dev/null 2>&1
done

echo; printf '%s\n' "${results[@]}"
[[ " ${results[*]} " != *FAILED* ]]
