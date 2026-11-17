"""End-to-end check: layouts travel with the metadata export/import.

source = dev snello-control (:8080, PostgreSQL, OIDC on)  target = all-in-one (:18080, H2 in memory, auth off)
"""
import io
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

SRC, DST = "http://localhost:8080", "http://127.0.0.1:18080"
TOKEN = open(sys.argv[1]).read().strip()
COMPOSE = ["docker", "compose", "-f", sys.argv[2], "restart"]
results = []


def http(method, url, body=None, token=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", ctype)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            payload = r.read()
            return r.status, payload
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def j(method, url, body=None, token=None):
    status, payload = http(method, url, body, token)
    return status, (json.loads(payload) if payload else None)


def check(name, ok, detail=""):
    results.append(ok)
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail else ""))


def fresh_target():
    subprocess.run(COMPOSE, check=True, capture_output=True)
    for _ in range(90):
        try:
            if j("GET", DST + "/api/layouts")[0] == 200:
                return
        except Exception:
            pass
        time.sleep(1)
    raise SystemExit("target did not come back")


def meta_uuid(base, table):
    rows = j("GET", f"{base}/api/metadatas?table_name={table}")[1]
    return rows[0]["uuid"] if rows else None


def layouts(base):
    return {r["uuid"]: r for r in j("GET", base + "/api/layouts?_limit=1000")[1]}


def export(body):
    return j("POST", SRC + "/api/metadatas/export", body, TOKEN)


src_kayaks, src_brands = meta_uuid(SRC, "kayaks"), meta_uuid(SRC, "brands")
src_layouts = layouts(SRC)
src_kayak_layouts = {u: r for u, r in src_layouts.items() if r["metadata_name"] == "kayaks"}
print(f"source: kayaks={src_kayaks} brands={src_brands} layouts={len(src_layouts)} "
      f"(kayaks {len(src_kayak_layouts)})\n")

# ---------------------------------------------------------------- export
print("== export")
st, e_kayaks = export({"metadatas": [src_kayaks], "layouts": True})
exp = e_kayaks.get("config", {}).get("layouts", []) if st == 200 else []
check("JSON export of kayaks carries config.layouts", st == 200 and "layouts" in e_kayaks.get("config", {}), f"http {st}")
check("…exactly the kayaks layouts", {r["uuid"] for r in exp} == set(src_kayak_layouts), f"{len(exp)} rows")
check("…with json_data, page_type, metadata_name, updated_at",
      all({"json_data", "page_type", "metadata_name", "metadata_uuid", "updated_at", "name"} <= r.keys() for r in exp))

st, e_brands = export({"metadatas": [src_brands], "layouts": True})
check("brands export carries only the brands layout",
      [r["metadata_name"] for r in e_brands["config"]["layouts"]] == ["brands"])

st, e_all = export({"metadatas": [], "layouts": True})
check("config-only export (no metadata) carries every layout",
      {r["uuid"] for r in e_all["config"]["layouts"]} == set(src_layouts), f"{len(e_all['config']['layouts'])} rows")

st, e_none = export({"metadatas": [src_kayaks]})
check("without the flag there is no layouts section", "layouts" not in (e_none.get("config") or {}))

st, zbytes = http("POST", SRC + "/api/metadatas/export", {"metadatas": [src_kayaks], "layouts": True, "format": "zip"}, TOKEN)
z = zipfile.ZipFile(io.BytesIO(zbytes))
manifest = json.loads(z.read("manifest.json"))
zmeta = json.loads(z.read("metadatas.json"))
check("ZIP manifest counts the layouts", manifest.get("config", {}).get("layouts") == len(src_kayak_layouts), str(manifest.get("config")))
check("ZIP metadatas.json carries config.layouts", len(zmeta["config"]["layouts"]) == len(src_kayak_layouts))

# ---------------------------------------------------------------- import: full JSON into an empty target
print("\n== import JSON (metadata + layouts) into an empty H2 target")
fresh_target()
st, res = j("POST", DST + "/api/metadatas/import", e_kayaks)
lr = (res or {}).get("config", {}).get("layouts", {})
check("import answers 200 with a layouts result", st == 200 and bool(lr), f"http {st} {lr}")
check("3 created, 0 errors", lr.get("created") == len(src_kayak_layouts) and not lr.get("errors"), str(lr))
dst_kayaks = meta_uuid(DST, "kayaks")
got = layouts(DST)
check("target layouts point at the target's kayaks metadata",
      got and all(r["metadata_uuid"] == dst_kayaks and r["metadata_name"] == "kayaks" for r in got.values()),
      f"target kayaks={dst_kayaks}")
same_body = all(json.loads(got[u]["json_data"]) == json.loads(src_kayak_layouts[u]["json_data"]) for u in src_kayak_layouts if u in got)
check("same uuids and identical json_data bodies", set(got) == set(src_kayak_layouts) and same_body)
check("each layout readable at GET /api/layouts/{uuid}", all(j("GET", f"{DST}/api/layouts/{u}")[0] == 200 for u in got))

print("\n== re-import is an overwrite, not a duplicate")
st, res = j("POST", DST + "/api/metadatas/import", {"config": e_kayaks["config"]})
lr = res["config"]["layouts"]
check("0 created, 3 updated", lr.get("created") == 0 and lr.get("updated") == len(src_kayak_layouts), str(lr))
check("still 3 layouts in the target", len(layouts(DST)) == len(src_kayak_layouts))

# ---------------------------------------------------------------- import: metadata uuid differs in the target
print("\n== target already has 'kayaks' with ANOTHER uuid (config-only import)")
fresh_target()
st, created = j("POST", DST + "/api/metadatas", {"table_name": "kayaks", "table_key": "uuid", "table_key_type": "uuid",
                                                 "description": "pre-existing", "already_exist": False, "created": False})
other = created["uuid"]
check("target kayaks uuid differs from the source", other != src_kayaks, f"{other[:8]} vs {src_kayaks[:8]}")
st, res = j("POST", DST + "/api/metadatas/import", {"config": {"layouts": e_kayaks["config"]["layouts"]}})
lr = res["config"]["layouts"]
check("3 created, 0 errors", lr.get("created") == len(src_kayak_layouts) and not lr.get("errors"), str(lr))
check("metadata_uuid re-pointed to the target's kayaks", all(r["metadata_uuid"] == other for r in layouts(DST).values()))

# ---------------------------------------------------------------- import: rejected rows
print("\n== invalid rows are reported, not imported")
fresh_target()
st, res = j("POST", DST + "/api/metadatas/import", {"config": {"layouts": e_kayaks["config"]["layouts"]}})
lr = res["config"]["layouts"]
check("no kayaks metadata in the target: 0 created, 3 errors", lr.get("created") == 0 and len(lr.get("errors", [])) == 3,
      (lr.get("errors") or ["-"])[0])
j("POST", DST + "/api/metadatas/import", {"metadatas": e_kayaks["metadatas"]})
bad = dict(e_kayaks["config"]["layouts"][0], page_type="list",
           json_data=json.dumps({"version": 1, "kind": "wizard", "pages": []}))
st, res = j("POST", DST + "/api/metadatas/import", {"config": {"layouts": [bad]}})
lr = res["config"]["layouts"]
check("page_type list with a wizard body is rejected", lr.get("created") == 0 and len(lr.get("errors", [])) == 1,
      (lr.get("errors") or ["-"])[0])
check("nothing was written", len(layouts(DST)) == 0)

# ---------------------------------------------------------------- import: ZIP
print("\n== import the ZIP into an empty target")
fresh_target()
st, payload = http("POST", DST + "/api/metadatas/import", raw=zbytes, ctype="application/zip")
res = json.loads(payload) if payload else {}
lr = res.get("config", {}).get("layouts", {})
check("ZIP import creates the 3 layouts", st == 200 and lr.get("created") == len(src_kayak_layouts), f"http {st} {lr}")
dst_kayaks = meta_uuid(DST, "kayaks")
check("…pointing at the target's kayaks", all(r["metadata_uuid"] == dst_kayaks for r in layouts(DST).values()))

print(f"\n{sum(results)}/{len(results)} checks passed")
sys.exit(0 if all(results) else 1)
