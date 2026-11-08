#!/usr/bin/env bash
# Upsert AI catalog fixture into a Snello control/all-in-one API (default api.sne.lol).
#
# Works even if the server regenerates UUID on POST: we remap children to the
# project uuid returned by the API (matched by name on re-runs).
#
# Usage:
#   ./dev/scripts/import-ai-catalog.sh
#   AIO_BASE_URL=https://api.sne.lol ./dev/scripts/import-ai-catalog.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env.test}"
BASE="${AIO_BASE_URL:-https://api.sne.lol}"
FIXTURE="${AI_CATALOG_PATH:-$ROOT/dev/exports/flower-ai-kayaklove.json}"
TOKEN_URL="${TOKEN_URL:-https://accounts.kayak.love/realms/alphasnello/protocol/openid-connect/token}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing $ENV_FILE" >&2
  exit 1
fi
if [[ ! -f "$FIXTURE" ]]; then
  echo "missing fixture $FIXTURE" >&2
  exit 1
fi
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
: "${CLIENT_ID:?CLIENT_ID required}"
: "${CLIENT_SECRET:?CLIENT_SECRET required}"

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

echo "[ai-import] mint token → ${TOKEN_URL}"
curl -sS -X POST "$TOKEN_URL" \
  -d "grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}" \
  -o "$WORKDIR/token.json"
TOKEN=$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1]));
assert "access_token" in d, d
print(d["access_token"])' "$WORKDIR/token.json")

echo "[ai-import] target=${BASE} fixture=${FIXTURE}"
export AIO_BASE_URL="$BASE" SNELLO_TOKEN="$TOKEN" AI_CATALOG_PATH="$FIXTURE"
python3 <<'PY'
import json, os, urllib.parse, urllib.request, urllib.error

base = os.environ["AIO_BASE_URL"].rstrip("/")
token = os.environ["SNELLO_TOKEN"]
fixture = json.load(open(os.environ["AI_CATALOG_PATH"]))

def req(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(
        base + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            **({"Content-Type": "application/json"} if body is not None else {}),
        },
    )
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            raw = resp.read().decode() or "null"
            return resp.status, json.loads(raw) if raw.strip() else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            parsed = json.loads(raw) if raw else {"msg": raw}
        except Exception:
            parsed = {"msg": raw[:500]}
        return e.code, parsed

def list_rows(path, limit=200):
    code, body = req("GET", f"{path}?_limit={limit}&_start=0")
    if code != 200:
        return []
    return body if isinstance(body, list) else []

def find_by_name(path, name):
    for row in list_rows(path):
        if row.get("name") == name:
            return row
    return None

def upsert_by_name(path, row, label):
    payload = {k: v for k, v in row.items() if v is not None}
    name = payload.get("name")
    existing = find_by_name(path, name) if name else None
    if existing and existing.get("uuid"):
        uuid = existing["uuid"]
        payload["uuid"] = uuid
        code, body = req("PUT", f"{path}/{uuid}", payload)
        action = "update"
    else:
        code, body = req("POST", path, payload)
        action = "create"
        if code in (200, 201) and isinstance(body, dict) and body.get("uuid"):
            payload["uuid"] = body["uuid"]
    ok = code in (200, 201)
    print(f"  {action} {label} {name} http={code} uuid={payload.get('uuid')}{' OK' if ok else ' FAIL '+str(body)[:180]}")
    return ok, payload.get("uuid") if ok else None

ok = fail = 0
project_uuid_map = {}

print("[ai-import] projects")
for row in fixture.get("projects") or []:
    desired = row.get("uuid")
    success, actual = upsert_by_name("/api/ai/projects", row, "project")
    if success and actual:
        ok += 1
        if desired:
            project_uuid_map[desired] = actual
        project_uuid_map[row["name"]] = actual
    else:
        fail += 1

def remap_project(row):
    out = dict(row)
    src = out.get("project_uuid")
    if src in project_uuid_map:
        out["project_uuid"] = project_uuid_map[src]
    return out

print("[ai-import] skills")
for row in fixture.get("skills") or []:
    success, _ = upsert_by_name("/api/ai/skills", remap_project(row), "skill")
    ok += 1 if success else 0
    fail += 0 if success else 1

print("[ai-import] mcp-tools")
for row in fixture.get("mcp_tools") or []:
    success, _ = upsert_by_name("/api/ai/mcp-tools", remap_project(row), "mcp")
    ok += 1 if success else 0
    fail += 0 if success else 1

print("[ai-import] cards")
for row in fixture.get("cards") or []:
    success, _ = upsert_by_name("/api/ai/cards", remap_project(row), "card")
    ok += 1 if success else 0
    fail += 0 if success else 1

print(f"[ai-import] project_uuid_map={project_uuid_map}")
print(f"[ai-import] done ok={ok} fail={fail}")
raise SystemExit(0 if fail == 0 else 3)
PY
