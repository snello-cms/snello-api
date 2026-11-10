#!/usr/bin/env bash
# Refresh committed seeds under dev/exports/ from live kayak.love (public GETs).
#
# Writes:
#   dev/exports/metadatas-from-pg.json   — {"metadatas":[{"metadata":{…},"fields":[…]}, …]}
#   dev/exports/snello-backup-data.zip   — full row backup
#   dev/exports/tables/<name>.zip        — optional per-table backups (EXPORT_PER_TABLE=1)
#
# Usage:
#   ./dev/scripts/export-from-kayaklove.sh
#   KAYAK_BASE_URL=https://kayak.love EXPORT_PER_TABLE=1 ./dev/scripts/export-from-kayaklove.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${OUT_DIR:-$ROOT/dev/exports}"
BASE="${KAYAK_BASE_URL:-https://kayak.love}"
PER_TABLE="${EXPORT_PER_TABLE:-0}"
META_OUT="$OUT/metadatas-from-pg.json"
DATA_OUT="$OUT/snello-backup-data.zip"
TABLES_DIR="$OUT/tables"

mkdir -p "$OUT"
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

echo "[kayak-export] GET ${BASE}/api/metadatas"
curl -sf "${BASE}/api/metadatas?_limit=100000" -o "$WORKDIR/metadatas.json"
echo "[kayak-export] GET ${BASE}/api/fielddefinitions"
curl -sf "${BASE}/api/fielddefinitions?_limit=100000" -o "$WORKDIR/fields.json"

python3 - "$WORKDIR/metadatas.json" "$WORKDIR/fields.json" "$META_OUT" <<'PY'
import json, sys
from collections import defaultdict

metas_path, fields_path, out_path = sys.argv[1:4]
metas = json.load(open(metas_path))
fields = json.load(open(fields_path))
by_uuid = defaultdict(list)
by_name = defaultdict(list)
for f in fields:
    if f.get("metadata_uuid"):
        by_uuid[f["metadata_uuid"]].append(f)
    if f.get("metadata_name"):
        by_name[f["metadata_name"]].append(f)

BOOL_META = ("api_protected", "calendar_enabled", "already_exist", "created")
BOOL_FIELD = ("input_disabled", "mandatory", "table_key", "searchable", "show_in_list")

def coerce_bools(row, keys):
    for k in keys:
        if row.get(k) is None:
            row[k] = False

export = {"metadatas": []}
missing = []
for m in metas:
    uuid = m.get("uuid")
    name = m.get("table_name")
    fds = by_uuid.get(uuid) or by_name.get(name) or []
    if not fds:
        missing.append(name)
    # H2 / Quarkus NOT NULL booleans reject SQL NULL from older MySQL dumps.
    coerce_bools(m, BOOL_META)
    for f in fds:
        coerce_bools(f, BOOL_FIELD)
    export["metadatas"].append({"metadata": m, "fields": fds})

with open(out_path, "w") as fh:
    json.dump(export, fh, indent=2, ensure_ascii=False)
    fh.write("\n")

print(f"metadatas={len(export['metadatas'])} fields={len(fields)} empty_field_sets={len(missing)}")
if missing:
    print("tables without fields:", ", ".join(missing[:20]), ("…" if len(missing) > 20 else ""))
# show metadata_group coverage (what broke SPA contents menu on sne)
from collections import Counter
groups = Counter((m.get("metadata_group") or "<null>") for m in metas)
print("metadata_group:", dict(groups))
PY

echo "[kayak-export] GET ${BASE}/api/backup/data → ${DATA_OUT}"
curl -sf "${BASE}/api/backup/data" -o "$DATA_OUT"
ls -lh "$META_OUT" "$DATA_OUT"

if [[ "$PER_TABLE" == "1" ]]; then
  mkdir -p "$TABLES_DIR"
  python3 - "$META_OUT" <<'PY' > "$WORKDIR/tables.txt"
import json, sys
for e in json.load(open(sys.argv[1]))["metadatas"]:
    n = (e.get("metadata") or {}).get("table_name")
    if n:
        print(n)
PY
  while read -r table; do
    [[ -z "$table" ]] && continue
    echo "[kayak-export] GET backup/data/${table}"
    curl -sf "${BASE}/api/backup/data/${table}" -o "${TABLES_DIR}/${table}.zip" || {
      echo "  WARN: failed ${table}" >&2
      rm -f "${TABLES_DIR}/${table}.zip"
    }
  done < "$WORKDIR/tables.txt"
  echo "[kayak-export] per-table zips in ${TABLES_DIR} ($(ls "$TABLES_DIR" | wc -l | tr -d ' ') files)"
fi

echo "[kayak-export] done"
echo "Next:"
echo "  # wipe + import into api.sne.lol (destructive):"
echo "  ./dev/scripts/wipe-and-import-sne.sh"
echo "  # or local AIO:"
echo "  ./dev/scripts/aio-import-from-exports.sh"
