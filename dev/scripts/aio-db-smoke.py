#!/usr/bin/env python3
"""Smoke test of one all-in-one container on its database, through the REST API as an Admin.

phase "write": creates table aio_dbtest (metadata + fields + DDL), then row create / read / update / search / delete.
phase "read":  after a container restart, the rows written before are still there (they live in the external DB).
Env: SNELLO_URL (http://localhost:18081), KEYCLOAK_TOKEN_URL (local dev realm), SNELLO_ADMIN_USER / _PASSWORD
(admin / admin: local dev realm only). Usage: aio-db-smoke.py write|read   Exit 1 on the first failure.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

SNELLO = os.environ.get("SNELLO_URL", "http://localhost:18081").rstrip("/")
TOKEN_URL = os.environ.get("KEYCLOAK_TOKEN_URL", "http://localhost:8180/realms/snello/protocol/openid-connect/token")
TABLE = "aio_dbtest"


def token():
    body = urllib.parse.urlencode({"grant_type": "password", "client_id": "snello-api",
                                   "username": os.environ.get("SNELLO_ADMIN_USER", "admin"),
                                   "password": os.environ.get("SNELLO_ADMIN_PASSWORD", "admin")}).encode()
    with urllib.request.urlopen(urllib.request.Request(TOKEN_URL, body)) as r:
        return json.load(r)["access_token"]


TOKEN = token()


def api(method, path, body=None, ok=(200, 201, 204)):
    req = urllib.request.Request(SNELLO + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json",
                                          "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            status, text = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    if status not in ok:
        fail(f"{method} {path} -> {status} {text[:400]}")
    try:
        return json.loads(text) if text.strip() else None
    except ValueError:
        return text


def check(cond, what, detail=""):
    if not cond:
        fail(f"{what} {detail}")
    print(f"PASS  {what}" + (f"  — {detail}" if detail else ""), flush=True)


def fail(msg):
    print(f"FAIL  {msg}", flush=True)
    sys.exit(1)


def rows(query=""):
    return api("GET", f"/api/{TABLE}?_limit=50{query}") or []


def write():
    for m in api("GET", f"/api/metadatas?table_name={TABLE}") or []:
        if m.get("table_name") == TABLE:   # rerun on a kept volume: start clean
            api("DELETE", f"/api/metadatas/{m['uuid']}", ok=(200, 204, 404))
    m = api("POST", "/api/metadatas", {"table_name": TABLE, "description": "all-in-one external DB smoke test",
                                        "table_key": "uuid", "table_key_type": "uuid", "metadata_group": "data",
                                        "select_fields": "uuid,title,status,happened_at"})
    fields = [("title", "input", {"input_type": "text", "searchable": True, "search_condition": "containss",
                                  "search_field_name": "title_containss"}),
              ("notes", "tinymce", {}),
              ("status", "select", {"options": "NEW,DONE"}),
              ("happened_at", "datetime", {})]
    for i, (name, type_, extra) in enumerate(fields, 1):
        api("POST", "/api/fielddefinitions", {"metadata_uuid": m["uuid"], "metadata_name": TABLE, "name": name,
                                              "label": name, "type": type_, "view_index": i, "order_num": i, **extra})
    api("POST", f"/api/metadatas/{m['uuid']}/create")
    check(api("GET", f"/api/metadatas/{m['uuid']}").get("created") in (True, "true", 1), "table created (DDL on this database)")

    a = api("POST", f"/api/{TABLE}", {"title": "Àlpha – unicode ✓", "notes": "<p>long text</p>", "status": "NEW",
                                       "happened_at": "2026-11-20T10:15:00Z"})
    b = api("POST", f"/api/{TABLE}", {"title": "beta", "status": "NEW"})
    check(bool(a.get("uuid")) and bool(b.get("uuid")), "2 rows created", a.get("uuid"))
    got = api("GET", f"/api/{TABLE}/{a['uuid']}")
    check(got.get("title") == "Àlpha – unicode ✓", "row read back, unicode intact", got.get("title"))
    api("PUT", f"/api/{TABLE}/{b['uuid']}", {**api("GET", f"/api/{TABLE}/{b['uuid']}"), "status": "DONE"})
    check(api("GET", f"/api/{TABLE}/{b['uuid']}").get("status") == "DONE", "row updated")
    hits = rows("&title_containss=LPHA")   # upper case vs "Àlpha" (not the À: accent rules differ per database)
    check([h["uuid"] for h in hits] == [a["uuid"]], "case-insensitive search", f"{len(hits)} hit")
    check(len(rows("&_limit=1&_start=1")) == 1, "paging (_limit / _start)")
    api("DELETE", f"/api/{TABLE}/{b['uuid']}")
    left = rows()
    check([r["uuid"] for r in left] == [a["uuid"]], "row deleted", f"{len(left)} left")


def read():
    left = rows()
    check(len(left) == 1 and left[0].get("title") == "Àlpha – unicode ✓", "after restart the row is still there", f"{len(left)} row")


{"write": write, "read": read}[sys.argv[1] if len(sys.argv) > 1 else "write"]()
