#!/usr/bin/env bash
# Export AI catalog (projects/skills/mcp-tools/cards) from flower (ai.kayak.love)
# into snello snake_case fixture JSON.
#
# Usage:
#   FLOWER_TOKEN='Bearer eyJ...' ./dev/scripts/export-flower-ai-catalog.sh
#   # or raw JWT:
#   FLOWER_TOKEN='eyJ...' ./dev/scripts/export-flower-ai-catalog.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FLOWER_BASE="${FLOWER_BASE:-https://ai.kayak.love}"
PROJECT_UUID="${PROJECT_UUID:-11111111-1111-4111-8111-111111111111}"
OUT="${OUT:-$ROOT/dev/exports/flower-ai-kayaklove.json}"
TEST_OUT="${TEST_OUT:-$ROOT/snello-control-app/src/test/resources/fixtures/flower-ai-kayaklove.json}"

: "${FLOWER_TOKEN:?FLOWER_TOKEN required (Bearer JWT from ai.kayak.love session)}"
AUTH="$FLOWER_TOKEN"
if [[ "$AUTH" != Bearer\ * ]]; then
  AUTH="Bearer ${AUTH}"
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

fetch() {
  local path="$1" dest="$2"
  curl -sS -H "Authorization: ${AUTH}" -H 'Accept: application/json' \
    "${FLOWER_BASE}${path}" -o "$dest"
  python3 - "$dest" <<'PY'
import json,sys
p=sys.argv[1]
d=json.load(open(p))
if isinstance(d, dict) and d.get("msg"):
    raise SystemExit(f"flower error for {p}: {d}")
if not isinstance(d, list):
    raise SystemExit(f"expected list in {p}, got {type(d)}")
print(len(d))
PY
}

echo "[flower-export] projects"
fetch "/api/v1/projects?startRow=0&pageSize=50&orderBy=name%20asc" "$WORKDIR/projects.json"
echo "[flower-export] skills project=${PROJECT_UUID}"
fetch "/api/v1/skills?startRow=0&pageSize=100&orderBy=name%20asc&obj.projectUuid=${PROJECT_UUID}" "$WORKDIR/skills.json"
echo "[flower-export] mcptools"
fetch "/api/v1/mcptools?startRow=0&pageSize=100&orderBy=name%20asc&obj.projectUuid=${PROJECT_UUID}" "$WORKDIR/mcptools.json"
echo "[flower-export] cards"
fetch "/api/v1/cards?startRow=0&pageSize=100&orderBy=name%20asc&obj.projectUuid=${PROJECT_UUID}" "$WORKDIR/cards.json"

python3 - "$WORKDIR" "$OUT" "$TEST_OUT" "$FLOWER_BASE" "$PROJECT_UUID" <<'PY'
import hashlib, json, sys, uuid
from pathlib import Path

wd, out, test_out, source, pid = sys.argv[1:6]

def stable_uuid(key: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, key))

def roles_csv(v):
    if isinstance(v, list):
        return ", ".join(str(x).strip() for x in v if x and str(x).strip())
    if isinstance(v, str):
        return v
    return ""

projects = json.load(open(f"{wd}/projects.json"))
skills = json.load(open(f"{wd}/skills.json"))
tools = json.load(open(f"{wd}/mcptools.json"))
cards = json.load(open(f"{wd}/cards.json"))

fixture = {
    "source": source,
    "project_uuid": pid,
    "projects": [{
        "uuid": p.get("uuid") or pid,
        "name": p["name"],
        "description": p.get("description") or "",
        "global_message": p.get("globalMessage") or "",
        "sso_group": p.get("ssoGroup") or "",
        "roles": p.get("roles") or "",
    } for p in projects],
    "skills": [{
        "uuid": stable_uuid(f"ai_skill:{pid}:{s['name']}"),
        "project_uuid": s.get("projectUuid") or pid,
        "name": s["name"],
        "description": s.get("description") or "",
        "instructions": s.get("instructions") or "",
        "allowed_roles": roles_csv(s.get("allowedRoles")),
        "enabled": s.get("enabled", True) is not False,
    } for s in skills],
    "mcp_tools": [{
        "uuid": stable_uuid(f"ai_mcp_tool:{pid}:{t['name']}"),
        "project_uuid": t.get("projectUuid") or pid,
        "name": t["name"],
        "description": t.get("description") or "",
        "http_method": t.get("httpMethod") or "GET",
        "url": t["url"],
        "parameters_json": t.get("parametersJson") or '{"type":"object","properties":{}}',
        "arg_binding": t.get("argBinding") or "QUERY",
        "arg_names_json": t.get("argNamesJson"),
        "fixed_query_json": t.get("fixedQueryJson"),
        "catalog_page_path_template": t.get("catalogPagePathTemplate"),
        "result_type": t.get("resultType"),
        "result_path": t.get("resultPath"),
        "authenticated": bool(t.get("authenticated")),
        "service_account_uuid": (
            None if t.get("serviceAccountId") in (None, "") else str(t.get("serviceAccountId"))
        ),
        "allowed_roles": roles_csv(t.get("allowedRoles")),
        "enabled": t.get("enabled", True) is not False,
    } for t in tools],
    "cards": [{
        "uuid": stable_uuid(f"ai_ui_card:{pid}:{c['name']}"),
        "project_uuid": c.get("projectUuid") or pid,
        "name": c["name"],
        "result_type": c.get("resultType"),
        "template": c.get("template"),
        "enabled": c.get("enabled", True) is not False,
    } for c in cards],
}

text = json.dumps(fixture, indent=2, ensure_ascii=False) + "\n"
for path in (out, test_out):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(text)
    print(f"wrote {path}")
print("counts", {k: len(fixture[k]) for k in ("projects", "skills", "mcp_tools", "cards")})
PY
