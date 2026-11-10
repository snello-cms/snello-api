#!/usr/bin/env bash
# Destructive: wipe business metadatas/tables on a Snello API, then import
# dev/exports/metadatas-from-pg.json + snello-backup-data.zip (or per-table zips).
#
# Defaults target https://api.sne.lol with .env.test client_credentials (alphasnello).
#
# Usage:
#   ./dev/scripts/wipe-and-import-sne.sh              # full zip data import
#   IMPORT_PER_TABLE=1 ./dev/scripts/wipe-and-import-sne.sh
#   AIO_BASE_URL=http://127.0.0.1:8080 ./dev/scripts/wipe-and-import-sne.sh
#
# Requires CONFIRM=YES to run.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env.test}"
BASE="${AIO_BASE_URL:-https://api.sne.lol}"
META="${META_PATH:-$ROOT/dev/exports/metadatas-from-pg.json}"
DATA="${DATA_PATH:-$ROOT/dev/exports/snello-backup-data.zip}"
TABLES_DIR="${TABLES_DIR:-$ROOT/dev/exports/tables}"
PER_TABLE="${IMPORT_PER_TABLE:-0}"
TOKEN_URL="${TOKEN_URL:-https://accounts.kayak.love/realms/alphasnello/protocol/openid-connect/token}"

if [[ "${CONFIRM:-}" != "YES" ]]; then
  echo "Refusing to wipe ${BASE} without CONFIRM=YES" >&2
  echo "Example: CONFIRM=YES ./dev/scripts/wipe-and-import-sne.sh" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing $ENV_FILE" >&2
  exit 1
fi
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
: "${CLIENT_ID:?CLIENT_ID required}"
: "${CLIENT_SECRET:?CLIENT_SECRET required}"

if [[ ! -f "$META" ]]; then
  echo "missing $META — run ./dev/scripts/export-from-kayaklove.sh first" >&2
  exit 1
fi
if [[ "$PER_TABLE" != "1" && ! -f "$DATA" ]]; then
  echo "missing $DATA" >&2
  exit 1
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

echo "[wipe-import] minting token (${TOKEN_URL})"
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
payload = tok.split(".")[1] + "=" * (-len(tok.split(".")[1]) % 4)
c = json.loads(base64.urlsafe_b64decode(payload))
roles = ((c.get("resource_access") or {}).get("snello-api") or {}).get("roles") or []
print("has_Admin", "Admin" in roles or "admin" in roles, "roles", roles)
if "Admin" not in roles and "admin" not in roles:
    raise SystemExit("token lacks Admin role on snello-api")
PY
TOKEN=$(cat "$WORKDIR/access.tok")
AUTH=("Authorization: Bearer ${TOKEN}")

echo "[wipe-import] listing current metadatas on ${BASE}"
curl -sf -H "${AUTH[0]}" "${BASE}/api/metadatas?_limit=100000" -o "$WORKDIR/current.json" \
  || curl -sf "${BASE}/api/metadatas?_limit=100000" -o "$WORKDIR/current.json"

python3 - "$WORKDIR/current.json" "$BASE" "$TOKEN" <<'PY'
import json, subprocess, sys
metas = json.load(open(sys.argv[1]))
base, token = sys.argv[2], sys.argv[3]
print(f"wiping {len(metas)} metadatas")
ok_drop = fail_drop = ok_del = fail_del = 0
for m in metas:
    uuid = m.get("uuid")
    name = m.get("table_name")
    if not uuid:
        continue
    # 1) drop physical table if present
    r = subprocess.run(
        ["curl", "-s", "-o", "/tmp/wipe-drop.out", "-w", "%{http_code}",
         "-H", f"Authorization: Bearer {token}",
         "-X", "POST", f"{base}/api/metadatas/{uuid}/delete"],
        capture_output=True, text=True, check=False)
    code = (r.stdout or "").strip()
    if code in ("200", "204"):
        ok_drop += 1
    else:
        fail_drop += 1
        print(f"  drop WARN {name} http={code} {open('/tmp/wipe-drop.out').read()[:120]}")
    # 2) delete metadata row (+ fielddefinitions)
    r = subprocess.run(
        ["curl", "-s", "-o", "/tmp/wipe-del.out", "-w", "%{http_code}",
         "-H", f"Authorization: Bearer {token}",
         "-X", "DELETE", f"{base}/api/metadatas/{uuid}"],
        capture_output=True, text=True, check=False)
    code = (r.stdout or "").strip()
    if code in ("200", "204"):
        ok_del += 1
        print(f"  deleted {name}")
    else:
        fail_del += 1
        print(f"  delete FAIL {name} http={code} {open('/tmp/wipe-del.out').read()[:160]}")
print(f"drop ok={ok_drop} warn={fail_drop}; delete ok={ok_del} fail={fail_del}")
if fail_del:
    raise SystemExit("wipe incomplete")
PY

echo "[wipe-import] POST /api/metadatas/import"
IMP_CODE=$(curl -sS -o "$WORKDIR/import.body" -w "%{http_code}" \
  -H "${AUTH[0]}" -H "Content-Type: application/json" \
  -X POST "${BASE}/api/metadatas/import" --data-binary @"$META")
echo "import_http=${IMP_CODE}"
python3 -m json.tool "$WORKDIR/import.body" 2>/dev/null | head -40 || head -c 600 "$WORKDIR/import.body"
echo
if [[ "$IMP_CODE" != "200" ]]; then
  echo "metadata import failed" >&2
  exit 3
fi

echo "[wipe-import] POST /api/metadatas/{uuid}/create"
export AIO_BASE_URL="$BASE" SNELLO_TOKEN="$TOKEN" META_PATH="$META"
python3 - <<'PY'
import json, subprocess, os
base = os.environ["AIO_BASE_URL"]
token = os.environ["SNELLO_TOKEN"]
metas = json.load(open(os.environ["META_PATH"]))["metadatas"]
ok = fail = 0
for entry in metas:
    m = entry["metadata"]
    uuid, name = m.get("uuid"), m.get("table_name")
    if not uuid:
        fail += 1
        continue
    r = subprocess.run(
        ["curl", "-s", "-o", "/tmp/wipe-create.out", "-w", "%{http_code}",
         "-H", f"Authorization: Bearer {token}",
         "-X", "POST", f"{base}/api/metadatas/{uuid}/create"],
        capture_output=True, text=True, check=False)
    code = (r.stdout or "").strip()
    if code == "200":
        ok += 1
        print(f"  create ok {name}")
    else:
        fail += 1
        print(f"  create FAIL {name} http={code} {open('/tmp/wipe-create.out').read()[:200]}")
print(f"tables created ok={ok} fail={fail}")
PY

if [[ "$PER_TABLE" == "1" ]]; then
  echo "[wipe-import] per-table POST /api/backup/data from ${TABLES_DIR}"
  if [[ ! -d "$TABLES_DIR" ]]; then
    echo "missing ${TABLES_DIR} — re-run export with EXPORT_PER_TABLE=1" >&2
    exit 4
  fi
  for zip in "$TABLES_DIR"/*.zip; do
    [[ -f "$zip" ]] || continue
    name=$(basename "$zip" .zip)
    echo "  import ${name}"
    CODE=$(curl -sS -o "$WORKDIR/bak-${name}.body" -w "%{http_code}" \
      -H "${AUTH[0]}" \
      -F "file=@${zip};type=application/zip;filename=${name}.zip" \
      "${BASE}/api/backup/data")
    echo "    http=${CODE} $(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print("imported",d.get("imported"),"skipped",d.get("skipped"),"errors",len(d.get("errors") or []))' "$WORKDIR/bak-${name}.body" 2>/dev/null || head -c 120 "$WORKDIR/bak-${name}.body")"
  done
else
  echo "[wipe-import] POST /api/backup/data (full zip)"
  BAK_CODE=$(curl -sS -o "$WORKDIR/backup.body" -w "%{http_code}" \
    -H "${AUTH[0]}" \
    -F "file=@${DATA};type=application/zip;filename=snello-backup-data.zip" \
    "${BASE}/api/backup/data")
  echo "backup_http=${BAK_CODE}"
  python3 -m json.tool "$WORKDIR/backup.body" 2>/dev/null | head -80 || head -c 800 "$WORKDIR/backup.body"
  echo
fi

echo "[wipe-import] verify"
curl -sf "${BASE}/api/datalist/names" | python3 -c 'import sys,json; n=json.load(sys.stdin); print("tables", len(n))'
curl -sf "${BASE}/api/metadatas/groups" | python3 -c 'import sys,json; print("groups", json.load(sys.stdin))'
curl -sf "${BASE}/api/contents?_limit=1" | python3 -c 'import sys,json; d=json.load(sys.stdin); print("contents_sample", len(d), (d[0].get("title") if d else None))' || true

echo "[wipe-import] done → ${BASE}"
