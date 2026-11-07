#!/usr/bin/env bash
# Import metadatas + backup ZIP into https://api.sne.lol using .env.test client credentials.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env.test}"
BASE="${AIO_BASE_URL:-https://api.sne.lol}"
META="${META_PATH:-$ROOT/dev/exports/metadatas-from-pg.json}"
DATA="${DATA_PATH:-$ROOT/dev/exports/snello-backup-data.zip}"
TOKEN_URL="${TOKEN_URL:-https://accounts.kayak.love/realms/alphasnello/protocol/openid-connect/token}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing $ENV_FILE" >&2
  exit 1
fi
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
: "${CLIENT_ID:?CLIENT_ID required}"
: "${CLIENT_SECRET:?CLIENT_SECRET required}"

if [[ ! -f "$META" || ! -f "$DATA" ]]; then
  echo "missing exports: $META / $DATA" >&2
  exit 1
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

echo "[sne-lol] minting token from alphasnello (client_credentials)"
curl -sS -X POST "$TOKEN_URL" \
  -d "grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}" \
  -o "$WORKDIR/token.json"
python3 - "$WORKDIR/token.json" "$WORKDIR/access.tok" <<'PY'
import json, base64, sys
src, dst = sys.argv[1], sys.argv[2]
d = json.load(open(src))
if "access_token" not in d:
    raise SystemExit(f"token error: {d}")
tok = d["access_token"]
open(dst, "w").write(tok)
payload = tok.split(".")[1]
payload += "=" * (-len(payload) % 4)
c = json.loads(base64.urlsafe_b64decode(payload))
roles = ((c.get("resource_access") or {}).get("snello-api") or {}).get("roles") or []
print("has_Admin", "Admin" in roles or "admin" in roles, "roles", roles)
print("iss", c.get("iss"), "azp", c.get("azp"))
PY

TOKEN=$(cat "$WORKDIR/access.tok")
AUTH_H=("Authorization: Bearer ${TOKEN}")

echo "[sne-lol] probe auth (GET /api/auth)"
PROBE_CODE=$(curl -sS -o "$WORKDIR/probe.body" -w "%{http_code}" \
  -H "${AUTH_H[0]}" "${BASE}/api/auth")
echo "probe_http=${PROBE_CODE} body=$(head -c 160 "$WORKDIR/probe.body")"
if [[ "$PROBE_CODE" == "401" || "$PROBE_CODE" == "403" ]]; then
  echo "auth failed for writes (need Admin|Manager|User on resource_access/snello-api/roles)" >&2
  exit 2
fi

echo "[sne-lol] POST /api/metadatas/import"
IMP_CODE=$(curl -sS -o "$WORKDIR/import.body" -w "%{http_code}" \
  -H "${AUTH_H[0]}" -H "Content-Type: application/json" \
  -X POST "${BASE}/api/metadatas/import" --data-binary @"$META")
echo "import_http=${IMP_CODE}"
python3 - "$WORKDIR/import.body" <<'PY'
import json, sys
raw = open(sys.argv[1]).read()
print(raw[:500] if raw else "<empty>")
if raw.strip().startswith("{"):
    d = json.loads(raw)
    print("imported", len(d.get("imported") or []), "skipped", len(d.get("skipped") or []), "errors", d.get("errors"))
PY
if [[ "$IMP_CODE" != "200" ]]; then
  echo "metadata import failed" >&2
  exit 3
fi

echo "[sne-lol] GET /api/metadatas/{uuid}/create for each table"
export AIO_BASE_URL="$BASE" SNELLO_TOKEN="$TOKEN" META_PATH="$META"
python3 - <<'PY'
import json, subprocess, os
base = os.environ["AIO_BASE_URL"]
token = os.environ["SNELLO_TOKEN"]
metas = json.load(open(os.environ["META_PATH"]))["metadatas"]
ok = fail = 0
for entry in metas:
    m = entry["metadata"]
    uuid = m.get("uuid")
    name = m.get("table_name")
    if not uuid:
        print("skip (no uuid)", name)
        fail += 1
        continue
    r = subprocess.run(
        ["curl", "-s", "-o", "/tmp/sne-create.out", "-w", "%{http_code}",
         "-H", f"Authorization: Bearer {token}",
         f"{base}/api/metadatas/{uuid}/create"],
        capture_output=True, text=True, check=False)
    code = (r.stdout or "").strip()
    if code == "200":
        ok += 1
        print(f"  create ok {name}")
    else:
        fail += 1
        body = open("/tmp/sne-create.out").read()[:240]
        print(f"  create FAIL {name} http={code} {body}")
print(f"tables created ok={ok} fail={fail}")
PY

echo "[sne-lol] POST /api/backup/data"
BAK_CODE=$(curl -sS -o "$WORKDIR/backup.body" -w "%{http_code}" \
  -H "${AUTH_H[0]}" \
  -F "file=@${DATA};type=application/zip;filename=snello-backup-data.zip" \
  "${BASE}/api/backup/data")
echo "backup_http=${BAK_CODE}"
python3 -m json.tool "$WORKDIR/backup.body" 2>/dev/null | head -60 || head -c 800 "$WORKDIR/backup.body"
echo

echo "[sne-lol] verify"
curl -sS "${BASE}/api/datalist/names" | python3 -c 'import sys,json; n=json.load(sys.stdin); print("tables", len(n) if isinstance(n,list) else n)'
curl -sS -D "$WORKDIR/kayaks.hdr" -o "$WORKDIR/kayaks.list" "${BASE}/api/kayaks?_limit=2" || true
tr -d "\r" <"$WORKDIR/kayaks.hdr" | grep -i x-total-count || true
python3 - "$WORKDIR/kayaks.list" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
if not p.exists() or not p.read_text().strip():
    print("kayaks: empty/missing")
else:
    rows = json.loads(p.read_text())
    print("kayaks returned", len(rows))
    if rows:
        print("first name=", rows[0].get("name"), "brand=", rows[0].get("brand"))
PY

echo "[sne-lol] done → ${BASE}"
