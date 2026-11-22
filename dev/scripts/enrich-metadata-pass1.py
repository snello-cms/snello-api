#!/usr/bin/env python3
"""Pass-1 metadata enrichment against a live Snello API (default https://mssql.sne.lol).

Pages with _limit=50. Writes metadata_group / description / ai_profile and field labels.
Tracks complete|partial|blocked in a local report (ai_profile strips unknown keys remotely).

Env:
  SNELLO_URL          default https://mssql.sne.lol
  SNELLO_TOKEN        Bearer access token (optional if CLIENT_ID/SECRET set)
  CLIENT_ID / CLIENT_SECRET / SNELLO_TOKEN_URL
                      client_credentials refresh (token TTL is often 300s)
  ENRICH_REPORT       default dev/exports/enrichment-pass1.json
  ENRICH_DRY_RUN=1    no PUT
  ENRICH_MAX_META     optional cap on metadata updates (debug)
  ENRICH_MAX_FIELDS   optional cap on field updates (debug)
  ENRICH_SLEEP        pause between PUTs (default 0.25)
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

BASE = os.environ.get("SNELLO_URL", "https://mssql.sne.lol").rstrip("/")
TOKEN = os.environ.get("SNELLO_TOKEN", "")
TOKEN_URL = os.environ.get(
    "SNELLO_TOKEN_URL",
    "https://accounts.kayak.love/realms/alphasnello/protocol/openid-connect/token",
)
CLIENT_ID = os.environ.get("CLIENT_ID", "")
CLIENT_SECRET = os.environ.get("CLIENT_SECRET", "")
_TOKEN_EXPIRES_AT = 0.0  # epoch seconds; refresh a bit before
PAGE = 50
DRY = os.environ.get("ENRICH_DRY_RUN", "") in ("1", "true", "yes")
ROOT = Path(__file__).resolve().parents[2]
REPORT = Path(os.environ.get("ENRICH_REPORT", str(ROOT / "dev/exports/enrichment-pass1.json")))
MAX_META = int(os.environ["ENRICH_MAX_META"]) if os.environ.get("ENRICH_MAX_META") else None
MAX_FIELDS = int(os.environ["ENRICH_MAX_FIELDS"]) if os.environ.get("ENRICH_MAX_FIELDS") else None
SLEEP = float(os.environ.get("ENRICH_SLEEP", "0.25"))
SKIP_META = os.environ.get("ENRICH_SKIP_META", "") in ("1", "true", "yes")
# Only rewrite metadata when description still looks like introspect default / group missing.
META_ONLY_STALE = os.environ.get("ENRICH_META_STALE_ONLY", "1") in ("1", "true", "yes")
# Only load/process fields that look like unmapped keys or glued labels (faster on huge catalogs).
FIELDS_RELATIONS_ONLY = os.environ.get("ENRICH_RELATIONS_ONLY", "") in ("1", "true", "yes")
FIELDS_ONLY_TABLES = [
    t.strip() for t in os.environ.get("ENRICH_TABLES", "").split(",") if t.strip()
]
MAX_TABLES = int(os.environ["ENRICH_MAX_TABLES"]) if os.environ.get("ENRICH_MAX_TABLES") else None


def refresh_token(force: bool = False) -> str:
    """Keep Bearer token fresh (Keycloak client_credentials often expires in 300s)."""
    global TOKEN, _TOKEN_EXPIRES_AT
    now = time.time()
    if not force and TOKEN and now < (_TOKEN_EXPIRES_AT - 30):
        return TOKEN
    if not (CLIENT_ID and CLIENT_SECRET):
        if not TOKEN:
            raise SystemExit("SNELLO_TOKEN or CLIENT_ID/CLIENT_SECRET required for writes")
        return TOKEN
    data = urllib.parse.urlencode(
        {
            "grant_type": "client_credentials",
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
        }
    ).encode()
    req = urllib.request.Request(
        TOKEN_URL,
        data=data,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        payload = json.loads(r.read())
    TOKEN = payload["access_token"]
    expires_in = int(payload.get("expires_in") or 300)
    _TOKEN_EXPIRES_AT = time.time() + expires_in
    print(f"  token refreshed ttl={expires_in}s", flush=True)
    return TOKEN

TOKENS = sorted(
    {
        "prevalente", "descrizione", "fiscale", "cantiere", "mansioni",
        "mansione", "operative", "operativa", "sicurezza", "operatore", "operatori",
        "automezzi", "autovetture", "attrezzature",
        "attributi", "anagrafica", "articoli", "interinali", "integrazione", "presenze",
        "cauzioni", "fornitori", "intra", "aliquote", "allegati", "stampe",
        "budget", "contratto", "condizioni", "fattura", "ordine", "ordini", "magazzino",
        "consumo", "consumi", "righe", "causale", "causali", "movimento", "movimenti",
        "acquisti", "acquisto", "merce", "pallet", "scadenza", "qualita", "autorizzazione",
        "composizione", "dettaglio", "lavorazione", "campione", "importazione", "esterno",
        "container", "alfresco", "config", "societa", "agenzia", "agenti", "ammassi",
        "assicurazione", "tipologia", "cognome", "nome", "codice", "tipo", "data", "ora",
        "email", "telefono", "indirizzo", "comune", "provincia", "nazione", "partita",
        "username", "password", "creation", "created", "updated", "deleted", "description",
        "status", "active", "amount", "price", "quantity", "number", "value", "note", "notes",
        "cor", "tes", "rig", "sca", "lot", "det", "ext", "alt", "bar", "sku", "iva", "uuid",
        "id",
    },
    key=len,
    reverse=True,
)

PREFIX_GROUP = [
    (re.compile(r"^(a_|aa_|aaa_)?operatori", re.I), "Operatori"),
    (re.compile(r"^aaa_automezzi", re.I), "Automezzi"),
    (re.compile(r"^aaa_attributi", re.I), "Attrezzature"),
    (re.compile(r"^aaa_anagrafica", re.I), "Anagrafiche"),
    (re.compile(r"^aaa_magcau|^aa_regconsumi|^aaa_righe", re.I), "Magazzino"),
    (re.compile(r"^acq|^accorp", re.I), "Acquisti"),
    (re.compile(r"^anaart", re.I), "Anagrafiche articoli"),
    (re.compile(r"^ana", re.I), "Anagrafiche"),
    (re.compile(r"^aliq|^allegati", re.I), "Configurazione"),
    (re.compile(r"^age", re.I), "Agenti"),
    (re.compile(r"^ammassi", re.I), "Ammassi"),
    (re.compile(r"^alfresco", re.I), "Documenti"),
    (re.compile(r"^abs", re.I), "ABS"),
    (re.compile(r"^agg_", re.I), "Integrazioni"),
    (re.compile(r"^abicab$", re.I), "Riferimenti"),
]


def http(method: str, path: str, body=None, auth=False, retries: int = 8):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    last_err = None
    for attempt in range(retries):
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if auth:
            headers["Authorization"] = f"Bearer {refresh_token()}"
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                raw = r.read()
                total = r.headers.get("x-total-count") or r.headers.get("X-Total-Count")
                payload = json.loads(raw) if raw else None
                return r.status, payload, int(total) if total else None
        except urllib.error.HTTPError as e:
            err = e.read()[:800]
            if e.code == 401 and auth and attempt + 1 < retries:
                print(f"  retry {method} {path} http=401 refreshing token", flush=True)
                refresh_token(force=True)
                time.sleep(0.5)
                last_err = RuntimeError(f"{method} {path} -> 401: {err}")
                continue
            if e.code in (429, 502, 503, 504) and attempt + 1 < retries:
                wait = min(30.0, 2.0 * (attempt + 1) ** 1.5)
                print(f"  retry {method} {path} http={e.code} wait={wait:.1f}s", flush=True)
                time.sleep(wait)
                last_err = RuntimeError(f"{method} {path} -> {e.code}: {err}")
                continue
            raise RuntimeError(f"{method} {path} -> {e.code}: {err}") from e
        except Exception as e:
            last_err = e
            if attempt + 1 < retries:
                wait = min(45.0, 3.0 * (attempt + 1) ** 1.5)
                print(f"  retry {method} {path} err={e} wait={wait:.1f}s", flush=True)
                time.sleep(wait)
                continue
            raise
    raise last_err  # pragma: no cover


def page_get(path: str) -> list:
    out = []
    start = 0
    total = None
    while True:
        sep = "&" if "?" in path else "?"
        q = f"{path}{sep}_limit={PAGE}&_start={start}"
        _, chunk, hdr_total = http("GET", q)
        if hdr_total is not None:
            total = hdr_total
        if not isinstance(chunk, list):
            raise RuntimeError(f"expected list from {q}: {chunk}")
        out.extend(chunk)
        print(f"  GET {path} start={start} +{len(chunk)} acc={len(out)}"
              + (f"/{total}" if total else ""), flush=True)
        if not chunk:
            break
        if total is not None and len(out) >= total:
            break
        if len(chunk) < PAGE:
            break
        start += PAGE
        time.sleep(SLEEP)
    return out


def peel_prefix(name: str) -> str:
    core = name
    for p in ("aaa_", "aa_", "a_"):
        if core.lower().startswith(p):
            return core[len(p):]
    return core


def split_glued(name: str) -> str:
    if not name:
        return name
    s = re.sub(r"([a-z])([A-Z])", r"\1 \2", name)
    s = s.replace("_", " ").replace("-", " ")
    s = re.sub(r"\s+", " ", s).strip()
    if " " in s and not re.search(r"[a-z]{14,}", s.lower()):
        return s[:1].upper() + s[1:]

    lower = re.sub(r"[^a-z0-9]", "", s.lower())
    parts = []
    i = 0
    while i < len(lower):
        matched = False
        for tok in TOKENS:
            if not lower.startswith(tok, i):
                continue
            if tok == "id" and i == 0:
                continue
            parts.append(tok)
            i += len(tok)
            matched = True
            break
        if not matched:
            j = i + 1
            while j < len(lower):
                if any(lower.startswith(tok, j) for tok in TOKENS if len(tok) > 2):
                    break
                j += 1
            parts.append(lower[i:j])
            i = j
    text = " ".join(p for p in parts if p) or s
    return text[:1].upper() + text[1:]


def human_table_title(table: str) -> str:
    return split_glued(peel_prefix(table))


def assign_group(table: str, existing_groups: Counter) -> str:
    for rx, group in PREFIX_GROUP:
        if rx.search(table):
            return group
    core = peel_prefix(table)
    token = re.split(r"[_\s]+", core)[0]
    title = split_glued(token)
    head = title.split()[0] if title else "Altro"
    if len(head) <= 2:
        return "Altro"
    for g in existing_groups:
        if g.lower().startswith(head.lower()[:4]):
            return g
    return head


def guess_join_target(field_name: str, known: set[str]) -> str | None:
    n = field_name.lower()
    base = None
    for suf in ("_uuid", "_id", "uuid", "id"):
        if n.endswith(suf) and len(n) > len(suf):
            base = n[: -len(suf)]
            break
    if not base:
        return None
    candidates = [base, base + "s", base.rstrip("s"), "aaa_" + base, "a_" + base, "aa_" + base]
    for c in list(candidates):
        candidates.append(c.replace("prevalente", "").replace("codice", ""))
    for c in candidates:
        c = c.strip("_")
        if c in known:
            return c
    for t in known:
        tl = t.lower()
        if tl == base or tl.endswith("_" + base) or peel_prefix(tl) == base:
            return t
    return None


def sample_rows(table: str) -> list:
    try:
        _, rows, _ = http("GET", f"/api/{urllib.parse.quote(table)}?_limit={PAGE}")
        return rows if isinstance(rows, list) else []
    except Exception as e:
        print(f"  sample fail {table}: {e}", flush=True)
        return []


def row_get(row: dict, col: str):
    if col in row:
        return row.get(col)
    cl = col.lower()
    for k, v in row.items():
        if str(k).lower() == cl:
            return v
    return None


def cardinality(rows: list, col: str) -> tuple[int, int]:
    vals = []
    for r in rows:
        v = row_get(r, col)
        if v is None or v == "":
            continue
        vals.append(v)
    if not vals:
        return 0, 0
    c = Counter(vals)
    return len(c), max(c.values())


def looks_multivalue(val) -> bool:
    """Owner-column multijoin storage: comma/semicolon/JSON list of keys."""
    if val is None:
        return False
    s = str(val).strip()
    if not s:
        return False
    if s.startswith("[") and s.endswith("]"):
        return True
    if ("," in s or ";" in s) and re.search(r"\w", s):
        parts = re.split(r"[,;]", s)
        return len([p for p in parts if p.strip()]) >= 2
    return False


def owner_key_multiplicity(rows: list, owner_key: str, fk_col: str) -> int:
    """Max distinct FK values for the same owner PK in the sample (bridge / child side)."""
    buckets: dict = defaultdict(set)
    for r in rows:
        ok = row_get(r, owner_key)
        fk = row_get(r, fk_col)
        if ok is None or ok == "" or fk is None or fk == "":
            continue
        buckets[ok].add(str(fk))
    if not buckets:
        return 0
    return max(len(v) for v in buckets.values())


def merge_ai_profile(existing, summary: str, synonyms: list[str]) -> str:
    profile = {}
    if existing:
        try:
            profile = json.loads(existing) if isinstance(existing, str) else dict(existing)
        except json.JSONDecodeError:
            profile = {}
    profile["summary"] = summary
    prev = [s for s in (profile.get("synonyms") or []) if not str(s).startswith("_snello_")]
    merged = []
    for s in synonyms + prev:
        if s and s not in merged:
            merged.append(s)
    profile["synonyms"] = merged[:12]
    return json.dumps(profile, ensure_ascii=False)


def needs_label_rewrite(name: str, label: str, new_label: str) -> bool:
    if not name or new_label == label:
        return False
    # already humanized with spaces and not a single glued blob
    if label and " " in label and not re.search(r"[A-Za-z]{16,}", label):
        return False
    if not label or label.lower() == name.lower():
        return True
    if " " not in label and (len(name) >= 8 or name.lower().endswith("id") or re.search(r"[a-z][A-Z]", name)):
        return True
    return False


def main() -> int:
    print(f"base={BASE} dry={DRY} page={PAGE}", flush=True)
    metas = page_get("/api/metadatas")
    known = {m["table_name"] for m in metas}
    print(f"metadatas={len(metas)}", flush=True)

    group_counts: Counter = Counter()
    planned_groups = {}
    for m in metas:
        g = assign_group(m["table_name"], group_counts)
        planned_groups[m["table_name"]] = g
        group_counts[g] += 1
    print("groups:", dict(group_counts), flush=True)

    report = {
        "base": BASE,
        "pass": 1,
        "updated_at": datetime.now(timezone.utc).isoformat(),
        "page_size": PAGE,
        "entities": {},
        "stats": Counter(),
    }

    if MAX_TABLES is not None:
        metas = metas[:MAX_TABLES]
        print(f"ENRICH_MAX_TABLES={MAX_TABLES} → working on {len(metas)}", flush=True)

    meta_by_name = {m["table_name"]: m for m in metas}
    meta_updates = 0
    for m in metas:
        table = m["table_name"]
        title = human_table_title(table)
        group = planned_groups[table]
        synonyms = [title.lower()]
        peeled = peel_prefix(table).replace("_", " ")
        if peeled.lower() != title.lower():
            synonyms.append(peeled)

        report["entities"][table] = {
            "uuid": m["uuid"],
            "metadata_group": group,
            "description": title,
            "status": "complete",
            "missing_targets": [],
            "notes": [],
            "fields_touched": 0,
        }

        if SKIP_META:
            report["stats"]["metadata_skipped"] += 1
            continue
        if MAX_META is not None and meta_updates >= MAX_META:
            continue

        desc = m.get("description") or ""
        stale = (
            not m.get("metadata_group")
            or desc.startswith("Introspected from")
            or desc.strip() == ""
            or desc == table
        )
        if META_ONLY_STALE and not stale and m.get("metadata_group") == group:
            report["stats"]["metadata_unchanged"] += 1
            continue

        body = dict(m)
        body["metadata_group"] = group
        body["description"] = title
        body["ai_profile"] = merge_ai_profile(m.get("ai_profile"), title, synonyms)

        changed = (
            m.get("metadata_group") != group
            or m.get("description") != title
            or (m.get("ai_profile") or "") != body["ai_profile"]
        )
        if changed:
            if DRY:
                print(f"DRY meta {table} -> group={group} desc={title}", flush=True)
            else:
                try:
                    http("PUT", f"/api/metadatas/{m['uuid']}", body, auth=True)
                except Exception as e:
                    report["stats"]["metadata_put_errors"] += 1
                    print(f"WARN meta PUT {table}: {e}", flush=True)
                    report["entities"][table]["status"] = "partial"
                    report["entities"][table]["notes"].append(f"PUT fail: {e}")
                    time.sleep(max(SLEEP, 2.0))
                    continue
                time.sleep(SLEEP)
            meta_updates += 1
            report["stats"]["metadata_updated"] += 1
            if meta_updates % 5 == 0:
                print(f"  metadata updates={meta_updates}", flush=True)
            if meta_updates % 25 == 0:
                # Cool-down so liveness probes stay green under PUT storms.
                time.sleep(max(2.0, SLEEP * 4))
        else:
            report["stats"]["metadata_unchanged"] += 1

    print(f"metadata PUT done updates={meta_updates} skip={SKIP_META}", flush=True)

    work_metas = metas
    if FIELDS_ONLY_TABLES:
        wanted = set(FIELDS_ONLY_TABLES)
        work_metas = [m for m in metas if m["table_name"] in wanted]
        print(f"field scope tables={len(work_metas)}", flush=True)

    if MAX_FIELDS == 0:
        print("ENRICH_MAX_FIELDS=0 — skipping field phase", flush=True)
        report["stats"] = dict(report["stats"])
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"report -> {REPORT}", flush=True)
        print("stats", report["stats"], flush=True)
        return 0

    # Prefer per-table field pages (limit 50) to avoid hammering a single huge list endpoint.
    fields = []
    for i, m in enumerate(work_metas):
        table = m["table_name"]
        chunk = page_get(f"/api/fielddefinitions?metadata_name={urllib.parse.quote(table)}")
        fields.extend(chunk)
        if (i + 1) % 10 == 0:
            print(f"  fields loaded for {i+1}/{len(work_metas)} tables acc={len(fields)}", flush=True)
            time.sleep(0.4)
    print(f"fields={len(fields)}", flush=True)

    sample_cache: dict[str, list] = {}
    field_updates = 0

    for f in fields:
        if MAX_FIELDS is not None and field_updates >= MAX_FIELDS:
            break
        name = f.get("name") or ""
        label = f.get("label") or ""
        table = f.get("metadata_name") or ""
        new_label = split_glued(name)
        rewrite_label = needs_label_rewrite(name, label, new_label)

        ftype = (f.get("type") or "input").lower()
        join_target = f.get("join_table_name")
        rel_change = None
        rel_note = None

        candidate = name.lower().endswith(("id", "uuid")) or ftype in ("join", "multijoin")
        if candidate and table:
            target = join_target or guess_join_target(name, known)
            ent = report["entities"].setdefault(
                table,
                {"status": "complete", "missing_targets": [], "notes": [], "fields_touched": 0},
            )
            if target is None and ftype == "input" and name.lower().endswith(("id", "uuid")) and len(name) > 3:
                hint = name.lower()
                for suf in ("_uuid", "_id", "uuid", "id"):
                    if hint.endswith(suf):
                        hint = hint[: -len(suf)]
                        break
                hint = hint.strip("_")
                if len(hint) >= 3 and hint not in ent["missing_targets"]:
                    ent["missing_targets"].append(hint)
                    ent["status"] = "blocked"
                    ent["notes"].append(f"{name}: no join target in catalog")
                    report["stats"]["blocked_relations"] += 1
            elif target and target in known and ftype in ("input", "join"):
                if table not in sample_cache:
                    sample_cache[table] = sample_rows(table)
                rows = sample_cache[table]
                dist, max_share = cardinality(rows, name) if rows else (0, 0)
                meta_key = (meta_by_name.get(target) or {}).get("table_key") or "id"
                owner_pk = (meta_by_name.get(table) or {}).get("table_key") or "id"
                multi_per_owner = owner_key_multiplicity(rows, owner_pk, name) if rows else 0
                multi_cell = any(looks_multivalue(row_get(r, name)) for r in rows) if rows else False

                if multi_cell or multi_per_owner > 1:
                    # Same owner key → multiple related keys (or multi-value cell) → multijoin.
                    rel_change = {
                        "type": "multijoin",
                        "join_table_name": target,
                        "join_table_key": meta_key,
                    }
                    rel_note = (
                        f"promote {name} → multijoin {target} "
                        f"(multi_cell={multi_cell}, max_fk_per_owner={multi_per_owner})"
                    )
                    report["stats"]["multijoin_promotions"] += 1
                elif ftype == "input":
                    # N:1 toward target (FK value reused across rows is normal).
                    if max_share > 1:
                        rel_note = f"{name}: values reused (max {max_share}/sample) — N:1 join to {target}"
                    rel_change = {
                        "type": "join",
                        "join_table_name": target,
                        "join_table_key": meta_key,
                    }
                    rel_note = (rel_note or "") + f"; promote {name} input->join {target} (distinct={dist})"
                    report["stats"]["join_promotions"] += 1
                if rel_note:
                    ent["status"] = "partial" if ent.get("status") != "blocked" else "blocked"
                    ent["notes"].append(rel_note.strip("; ").strip())

        if FIELDS_RELATIONS_ONLY and not rel_change and not (
            rewrite_label and name.lower().endswith(("id", "uuid"))
        ):
            continue

        if not rewrite_label and not rel_change:
            continue

        body = dict(f)
        if rewrite_label:
            body["label"] = new_label
        if rel_change:
            body.update(rel_change)

        # Server rejects PUT when field name is not a legal SQL identifier (e.g. hyphen).
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name or ""):
            report["stats"]["fields_skipped_invalid_name"] += 1
            if table in report["entities"]:
                report["entities"][table].setdefault("notes", []).append(
                    f"skip PUT {name}: invalid column name"
                )
            continue

        if DRY:
            print(
                f"DRY field {table}.{name} label={body.get('label')} "
                f"type={body.get('type')} join={body.get('join_table_name')}",
                flush=True,
            )
        else:
            try:
                http("PUT", f"/api/fielddefinitions/{f['uuid']}", body, auth=True)
            except Exception as e:
                report["stats"]["fields_put_errors"] += 1
                print(f"WARN field PUT {table}.{name}: {e}", flush=True)
                if table in report["entities"]:
                    report["entities"][table].setdefault("notes", []).append(f"PUT fail {name}: {e}")
                    report["entities"][table]["status"] = "partial"
                continue
            time.sleep(SLEEP)
        field_updates += 1
        report["stats"]["fields_updated"] += 1
        if table in report["entities"]:
            report["entities"][table]["fields_touched"] = (
                report["entities"][table].get("fields_touched", 0) + 1
            )

    for ent in report["entities"].values():
        if ent.get("missing_targets"):
            ent["status"] = "blocked"
        elif ent.get("notes"):
            if ent.get("status") != "blocked":
                ent["status"] = "partial"
        else:
            ent["status"] = "complete"
        report["stats"][f"status_{ent['status']}"] += 1

    report["stats"] = dict(report["stats"])
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"report -> {REPORT}", flush=True)
    print("stats", report["stats"], flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        print("FATAL", e, file=sys.stderr)
        raise
