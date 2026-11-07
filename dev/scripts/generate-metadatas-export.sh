#!/usr/bin/env bash
# Dump → JSON export via control API (POST /api/metadatas/export {"all":true}).
#
# Steps:
#   1. Start Postgres with the that dump (compose-test.yml → host :5433)
#   2. Boot snello-control against that DB with OIDC off
#   3. Export all metadatas to the all-in-one IT fixture
#
# Usage (from repo root):
#   ./dev/scripts/generate-metadatas-export.sh
#   OUT=path/to/out.json ./dev/scripts/generate-metadatas-export.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT="${OUT:-$ROOT/snello-all-in-one-app/src/test/resources/fixtures/metadatas-from-dump.json}"
CONTROL_PORT="${CONTROL_PORT:-18081}"
PG_PORT="${PG_PORT:-5433}"
CONTROL_BASE="http://127.0.0.1:${CONTROL_PORT}"
CONTROL_PID=""

cleanup() {
  if [[ -n "${CONTROL_PID}" ]] && kill -0 "${CONTROL_PID}" 2>/dev/null; then
    echo "[export] stopping control pid=${CONTROL_PID}"
    kill "${CONTROL_PID}" 2>/dev/null || true
    wait "${CONTROL_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "[export] ensuring compose-test postgres on :${PG_PORT}"
docker compose -f compose-test.yml up -d
echo "[export] waiting for postgres..."
for i in $(seq 1 60); do
  if docker compose -f compose-test.yml exec -T postgresql pg_isready -U snello -d snello >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker compose -f compose-test.yml exec -T postgresql pg_isready -U snello -d snello

echo "[export] packaging control (skip tests)"
./mvnw -pl snello-control-app -am package -Dmaven.test.skip=true -q

JAR="$ROOT/snello-control-app/target/quarkus-app/quarkus-run.jar"
if [[ ! -f "$JAR" ]]; then
  echo "[export] missing $JAR" >&2
  exit 1
fi

echo "[export] starting control on :${CONTROL_PORT} against jdbc postgresql://localhost:${PG_PORT}/snello"
SNELLO_DB_KIND=postgresql \
SNELLO_DBTYPE=postgresql \
QUARKUS_DATASOURCE_DB_KIND=postgresql \
QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/snello" \
QUARKUS_DATASOURCE_USERNAME=snello \
QUARKUS_DATASOURCE_PASSWORD=snello \
QUARKUS_OIDC_ENABLED=false \
QUARKUS_SMALLRYE_JWT_ENABLED=false \
QUARKUS_HTTP_AUTH_PERMISSION_ROLES1_POLICY=permit \
QUARKUS_HTTP_AUTH_PERMISSION_AUTH_ADMIN_POLICY=permit \
QUARKUS_HTTP_PORT="${CONTROL_PORT}" \
QUARKUS_COMPOSE_DEVSERVICES_ENABLED=false \
java ${JAVA_OPTS:-} -jar "$JAR" >"/tmp/snello-export-control.log" 2>&1 &
CONTROL_PID=$!

echo "[export] waiting for control ${CONTROL_BASE} ..."
ready=0
for i in $(seq 1 90); do
  if curl -sf "${CONTROL_BASE}/q/health/ready" >/dev/null 2>&1 \
    || curl -sf "${CONTROL_BASE}/api/datalist/names" >/dev/null 2>&1; then
    ready=1
    break
  fi
  if ! kill -0 "${CONTROL_PID}" 2>/dev/null; then
    echo "[export] control died; log:" >&2
    tail -80 /tmp/snello-export-control.log >&2 || true
    exit 1
  fi
  sleep 2
done
if [[ "$ready" != "1" ]]; then
  echo "[export] control not ready; log:" >&2
  tail -80 /tmp/snello-export-control.log >&2 || true
  exit 1
fi

mkdir -p "$(dirname "$OUT")"
echo "[export] POST /api/metadatas/export {\"all\":true} → ${OUT}"
curl -sf -X POST "${CONTROL_BASE}/api/metadatas/export" \
  -H 'Content-Type: application/json' \
  -d '{"all":true}' \
  -o "$OUT"

python3 - <<PY
import json, sys
path = "${OUT}"
with open(path) as f:
    data = json.load(f)
metas = data.get("metadatas") or []
print(f"[export] wrote {len(metas)} metadata entries → {path}")
if not metas:
    sys.exit("export is empty — dump restore may have failed")
PY

echo "[export] done"
