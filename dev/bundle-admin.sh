#!/usr/bin/env bash
# Builds snello-admin and copies the dist into the all-in-one app's META-INF/resources, so Quarkus is the web server
# (admin at http://host:8080/, API at /api). config.json is rewritten to same-origin API paths.
#
# Usage: dev/bundle-admin.sh [--skip-build]
#   KEYCLOAK_URL / KEYCLOAK_REALM / KEYCLOAK_CLIENT_ID  override the SSO block (default: values from the admin's own config)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADMIN="$ROOT/snello-admin"
TARGET="$ROOT/snello-api/snello-all-in-one-app/src/main/resources/META-INF/resources"

if [[ "${1:-}" != "--skip-build" ]]; then
  echo "[bundle-admin] building $ADMIN"
  (cd "$ADMIN" && npx ng build --configuration production --base-href / --deploy-url /)
fi
DIST="$ADMIN/dist/snello-admin/browser"
[[ -f "$DIST/index.html" ]] || { echo "[bundle-admin] $DIST/index.html missing: build first"; exit 1; }

echo "[bundle-admin] copying $DIST -> $TARGET"
rm -rf "$TARGET"
mkdir -p "$TARGET"
cp -r "$DIST"/. "$TARGET"/

python3 - "$TARGET/assets/config.json" <<'PY'
import json, os, sys, re
path = sys.argv[1]
cfg = json.load(open(path))
def rel(v):  # https://host/api/x -> /api/x ; leave non-url values alone
    return re.sub(r'^https?://[^/]+', '', v) if isinstance(v, str) and v.startswith('http') else v
out = {k: (rel(v) if k.endswith('_path') else v) for k, v in cfg.items()}
kc = out.get('keycloakConfig', {})
kc['url'] = os.environ.get('KEYCLOAK_URL', kc.get('url'))
kc['realm'] = os.environ.get('KEYCLOAK_REALM', kc.get('realm'))
kc['clientId'] = os.environ.get('KEYCLOAK_CLIENT_ID', kc.get('clientId'))
out['keycloakConfig'] = kc
json.dump(out, open(path, 'w'), indent=2)
print('[bundle-admin] config.json -> same-origin paths, keycloak', kc)
PY
echo "[bundle-admin] done: $(find "$TARGET" -type f | wc -l) files"
