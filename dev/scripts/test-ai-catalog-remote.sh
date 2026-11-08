#!/usr/bin/env bash
# Targeted AI catalog checks against a remote Snello API (default api.sne.lol).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env.test}"
BASE="${AIO_BASE_URL:-https://api.sne.lol}"
FIXTURE="${AI_CATALOG_PATH:-$ROOT/dev/exports/flower-ai-kayaklove.json}"
TOKEN_URL="${TOKEN_URL:-https://accounts.kayak.love/realms/alphasnello/protocol/openid-connect/token}"
PROJECT_NAME="${PROJECT_NAME:-kayak.love}"

# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
: "${CLIENT_ID:?}"
: "${CLIENT_SECRET:?}"

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

curl -sS -X POST "$TOKEN_URL" \
  -d "grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}" \
  -o "$WORKDIR/token.json"
TOKEN=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["access_token"])' "$WORKDIR/token.json")

export AIO_BASE_URL="$BASE" SNELLO_TOKEN="$TOKEN" AI_CATALOG_PATH="$FIXTURE" PROJECT_NAME="$PROJECT_NAME"
python3 <<'PY'
import json, os, sys, urllib.request, urllib.error

base = os.environ["AIO_BASE_URL"].rstrip("/")
token = os.environ["SNELLO_TOKEN"]
fixture = json.load(open(os.environ["AI_CATALOG_PATH"]))
want_name = os.environ["PROJECT_NAME"]
want_skills = {s["name"] for s in fixture["skills"]}
want_tools = {t["name"] for t in fixture["mcp_tools"]}
want_cards = {c["name"] for c in fixture["cards"]}

def req(path):
    r = urllib.request.Request(
        base + path,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode() or "null")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]

failures = []

def check(cond, msg):
    if cond:
        print("  OK", msg)
    else:
        print("  FAIL", msg)
        failures.append(msg)

code, projects = req("/api/ai/projects?_limit=100")
check(code == 200, f"GET /api/ai/projects → {code}")
proj = next((p for p in (projects or []) if p.get("name") == want_name), None)
check(proj is not None, f"project '{want_name}' present")
pid = proj["uuid"] if proj else None

code, mine = req("/api/ai/me/projects")
check(code == 200, f"GET /api/ai/me/projects → {code}")
if isinstance(mine, list):
    check(any(p.get("name") == want_name for p in mine), "me/projects includes kayak.love")

if pid:
    code, catalog = req(f"/api/ai/card-catalog?projectUuid={pid}")
    check(code == 200, f"GET /api/ai/card-catalog → {code}")
    if isinstance(catalog, list):
        names = {c.get("name") for c in catalog}
        check(want_cards <= names, f"card-catalog has all fixture cards ({len(names)} got, need {len(want_cards)})")

code, skills = req("/api/ai/skills?_limit=200")
check(code == 200, f"GET /api/ai/skills → {code}")
if isinstance(skills, list):
    names = {s.get("name") for s in skills if not pid or s.get("project_uuid") == pid}
    check(want_skills <= names, f"skills names match ({sorted(want_skills - names)} missing)")

code, tools = req("/api/ai/mcp-tools?_limit=200")
check(code == 200, f"GET /api/ai/mcp-tools → {code}")
if isinstance(tools, list):
    names = {t.get("name") for t in tools if not pid or t.get("project_uuid") == pid}
    check(want_tools <= names, f"mcp-tools names match ({sorted(want_tools - names)} missing)")
    sample = next((t for t in tools if t.get("name") == "searchKayaks"), None)
    check(sample is not None and "kayak.love" in (sample.get("url") or ""), "searchKayaks points at kayak.love")

print()
if failures:
    print(f"FAILED {len(failures)}")
    sys.exit(1)
print("ALL CHECKS PASSED")
PY
