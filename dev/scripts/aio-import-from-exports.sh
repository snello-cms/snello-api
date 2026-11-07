#!/usr/bin/env bash
# Import metadatas-from-pg.json + snello-backup-data.zip into all-in-one
# using a Keycloak token minted on the compose-allinone-local network.
#
# Prerequisites:
#   docker compose -f compose-allinone-local.yml up -d --build
#   exports in dev/exports/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/dev/exports"
META="$OUT/metadatas-from-pg.json"
DATA="$OUT/snello-backup-data.zip"
export AIO_BASE_URL="${AIO_BASE_URL:-http://127.0.0.1:18080}"
export META_PATH="$META"
COMPOSE_NET="${COMPOSE_NET:-snello-allinone-local_default}"

if [[ ! -f "$META" || ! -f "$DATA" ]]; then
  echo "missing exports under $OUT" >&2
  exit 1
fi

echo "[aio-import] waiting for ${AIO_BASE_URL} ..."
for i in $(seq 1 90); do
  if curl -sf "${AIO_BASE_URL}/api/datalist/names" >/dev/null; then break; fi
  sleep 2
done
curl -sf "${AIO_BASE_URL}/api/datalist/names" >/dev/null

echo "[aio-import] minting Admin token via keycloak on compose network ${COMPOSE_NET}"
export SNELLO_TOKEN
SNELLO_TOKEN=$(docker run --rm --network "$COMPOSE_NET" curlimages/curl:8.12.1 -sf \
  -X POST 'http://keycloak:8080/realms/snello/protocol/openid-connect/token' \
  -d 'client_id=snello-api' -d 'username=admin' -d 'password=admin' -d 'grant_type=password' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')
echo "[aio-import] token_len=${#SNELLO_TOKEN}"

echo "[aio-import] POST /api/metadatas/import"
curl -sf -H "Authorization: Bearer ${SNELLO_TOKEN}" -H 'Content-Type: application/json' \
  -X POST "${AIO_BASE_URL}/api/metadatas/import" \
  --data-binary @"$META" | python3 -c 'import sys,json; d=json.load(sys.stdin); print("imported", len(d.get("imported") or []))'

echo "[aio-import] creating physical tables"
python3 - <<'PY'
import json, subprocess, os, sys
base = os.environ["AIO_BASE_URL"]
token = os.environ["SNELLO_TOKEN"]
metas = json.load(open(os.environ["META_PATH"]))["metadatas"]
ok = fail = 0
for entry in metas:
    m = entry["metadata"]
    uuid = m.get("uuid")
    name = m.get("table_name")
    if not uuid:
        print("skip (no uuid)", name); fail += 1; continue
    r = subprocess.run(
        ["curl", "-s", "-o", "/tmp/aio-create.out", "-w", "%{http_code}",
         "-H", f"Authorization: Bearer {token}",
         f"{base}/api/metadatas/{uuid}/create"],
        capture_output=True, text=True, check=False)
    code = (r.stdout or "").strip()
    if code == "200":
        ok += 1
        print(f"  create ok {name}")
    else:
        fail += 1
        body = open("/tmp/aio-create.out").read()[:240]
        print(f"  create FAIL {name} http={code} {body}")
print(f"tables created ok={ok} fail={fail}")
# Best-effort: dump schemas can have DDL issues (e.g. duplicate columns on users).
# Data import will report per-table errors for missing tables.
PY

echo "[aio-import] POST /api/backup/data"
curl -sf -H "Authorization: Bearer ${SNELLO_TOKEN}" \
  -F "file=@${DATA};type=application/zip;filename=snello-backup-data.zip" \
  "${AIO_BASE_URL}/api/backup/data" | python3 -m json.tool

echo "[aio-import] verify kayaks"
curl -sf -D /tmp/kayaks.hdr -o /tmp/kayaks.list "${AIO_BASE_URL}/api/kayaks?_limit=2"
tr -d '\r' </tmp/kayaks.hdr | grep -i x-total-count || true
python3 - <<'PY'
import json
rows=json.load(open("/tmp/kayaks.list"))
print("kayaks returned", len(rows))
if rows:
    print("first name=", rows[0].get("name"), "brand=", rows[0].get("brand"))
PY

echo "[aio-import] done → ${AIO_BASE_URL}"
