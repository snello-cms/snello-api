#!/usr/bin/env bash
# Full round-trip plan:
#   1) (optional) regenerate metadatas-from-dump.json from compose-test dump
#   2) build + start snello-all-in-one (compose-allinone-test.yml)
#   3) run Failsafe IT against http://127.0.0.1:18080
#
# Usage (repo root):
#   ./dev/scripts/run-aio-import-tests.sh
#   REGENERATE_EXPORT=1 ./dev/scripts/run-aio-import-tests.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

FIXTURE="$ROOT/snello-all-in-one-app/src/test/resources/fixtures/metadatas-from-dump.json"
AIO_BASE="${AIO_BASE_URL:-http://127.0.0.1:18080}"

if [[ "${REGENERATE_EXPORT:-0}" == "1" ]] || [[ ! -f "$FIXTURE" ]]; then
  echo "[aio-it] generating metadata export fixture"
  ./dev/scripts/generate-metadatas-export.sh
fi

if [[ ! -f "$FIXTURE" ]]; then
  echo "[aio-it] missing fixture: $FIXTURE" >&2
  echo "Run: ./dev/scripts/generate-metadatas-export.sh" >&2
  exit 1
fi

echo "[aio-it] packaging all-in-one"
./mvnw -pl snello-all-in-one-app -am package -Dmaven.test.skip=true -q

echo "[aio-it] recreating compose-allinone-test (fresh H2)"
docker compose -f compose-allinone-test.yml down -v >/dev/null 2>&1 || true
docker compose -f compose-allinone-test.yml up -d --build

echo "[aio-it] waiting for ${AIO_BASE} ..."
for i in $(seq 1 90); do
  if curl -sf "${AIO_BASE}/q/health/ready" >/dev/null 2>&1 \
    || curl -sf "${AIO_BASE}/api/datalist/names" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -sf "${AIO_BASE}/api/datalist/names" >/dev/null

echo "[aio-it] running Failsafe IT"
./mvnw -pl snello-all-in-one-app -am verify \
  -DskipITs=false \
  -Daio.baseUrl="${AIO_BASE}" \
  -Daio.fixture="${FIXTURE}"

echo "[aio-it] done (stack left up: docker compose -f compose-allinone-test.yml down)"
