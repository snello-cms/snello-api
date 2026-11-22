# Schema introspect (existing tables → metadata)

Control plane + all-in-one. Reverse of [drafts](drafts.md): read physical tables and propose `metadatas` + `fielddefinitions` with `already_exist=true`. Never creates or alters tables.

Base path: `/api/introspect`

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/introspect/tables` | Cheap catalog: counts + table names (`?q=` optional filter). No columns/samples. |
| `POST` | `/api/introspect/preview` | Derive rows; dry run (≤15 selected tables) |
| `POST` | `/api/introspect/apply` | Persist metadata + fields (Admin / Manager; ≤15 selected) |
| `POST` | `/api/introspect/batch` | Enqueue auto-apply of **all** remaining available tables; **202** `{uuid,status,…}` |
| `GET` | `/api/introspect/batch` | Recent jobs |
| `GET` | `/api/introspect/batch/{uuid}` | Job status + counters |
| `GET` | `/api/introspect/batch/{uuid}/logs?after=` | Append-only log lines (poll; `after` = last seen log uuid) |
| `POST` | `/api/introspect/batch/{uuid}/cancel` | Best-effort cancel between chunks |

Admin SPA: **Metadata → Introspect** (`/metadata/introspect`) for selective preview/apply (max 15) and full batch
import with a detachable job detail page (`/metadata/introspect/batch/:uuid`) that polls every ~2s.

### Catalog (`GET /tables`)

```json
{
  "total": 3004,
  "available": 2980,
  "already_referenced": 24,
  "tables": [{"name": "products", "referenced": false}]
}
```

### Selective preview / apply (`IntrospectRequest`)

```json
{
  "tables": ["products", "categories"],
  "sample_rows": 30,
  "metadata_group": "data"
}
```

- `tables`: optional allow-list; **an explicit list longer than 15 is rejected** (error). Omit/empty = every
  non-reserved user table not yet in metadata, **soft-capped to 15** (warning lists deferred names). Prefer the
  catalog + SPA select for interactive work, or `POST /batch` for the full remainder.
- Join/FK name matching still uses the full physical table catalog.
- `sample_rows`: rows sampled for the data heuristics below (default **5**, max 200). Use `0` for schema-only.
- Tables already referenced by current metadata (`table_name` or `alias_table`) are **always skipped**
- Row sampling / enum heuristics use dialect-native limits (`LIMIT` / `TOP`)

### Batch auto-apply

`POST /batch` body: `{ "sample_rows": 5, "metadata_group": "data" }` (both optional).

- Enqueues one job over all non-reserved, not-yet-referenced tables; returns **202** immediately.
- Worker applies in chunks of 15 via the same `apply` path; logs start/applied/skipped/errors in `introspect_job_logs`.
- Batch apply skips per-table CDI metadata events (cache invalidate only) so a large catalog does not reload
  on every table. Join-by-values only probes name-affine targets (capped), not every existing metadata.
- One concurrent batch per node (**409** if another is `queued`/`running`).
- JVM restart marks any `queued`/`running` job as `failed` (“interrupted by restart”); no resume in v1.
- Persistence: system tables `introspect_jobs` + `introspect_job_logs` (all dialects).

### Heuristics

What the schema states is read from JDBC metadata. What it does not state is guessed from column names and from
the rows (the sample, plus one `GROUP BY` per candidate column); every guess is listed in `warnings` so it can be
reviewed in the SPA before applying.

- **Primary key**: JDBC PK → `uuid` / `autoincrement` / `userdefined` (generated-style keys are not declared as fields).
  A varchar key whose values are slugs of another column (`my-title`, `my-title-1`) → `slug` + `table_key_addition`.
  Composite or missing primary keys are reported: only one column can be the key
- **Default order**: `position` / `sort` / `ordine` column → label column A-Z (`name`, `title`, …) → creation date, newest first
- **Scalars**: SQL type → `semantic_type` via `FieldUiDerivation`. `text` → textarea; a wide varchar is a textarea only
  when its values really are long (snello itself declares references as `varchar(2048)`); HTML tags in a few rows → tinymce
- **Join**: JDBC FK to a known table. Without an FK, a column named after a table (`*_id` / `*_uuid`, `brand` → `brands`,
  `video.type` → `video_types`) is a join only if its values exist among the target keys; with no rows the name is trusted.
  A column whose few distinct values are all keys of one table is a join too. `join_table_select_fields` is the
  target's label column
- **Select**: a short text column with a small closed set of values (2–30, each used twice on average) → `select`,
  options = the values found
- **Media**: values that are `documents.uuid`, or an `image` / `photo` / `logo`… column with no rows
- **Bridge tables** (`a_b` with `a_id` + `b_id`): generated as a table with two joins. A `multijoin` field keeps its
  values in a column of the owner table, so it cannot be backed by an existing bridge
- **Search form / list columns**: label-like columns, joins, selects, flags and tags; a list with fewer than three
  columns is topped up with the first short ones. Rich text, numbers and dates stay out
- **Mandatory**: `NOT NULL` without a default

### Always manual

Measured by wiping the metadata of a real database, regenerating it and diffing against the hand-made one
(19 tables, 122 fields). These cannot be read from schema or data and need a pass in the SPA:

- tables with no rows: key type `slug`, selects and value-confirmed joins need data
- select options are only the values already used, not the full domain (e.g. every country).
  System columns that hold them (`fielddefinitions.options`, `description`, `metadatas.search_fields`, …)
  are wide text (`nvarchar(max)` / `TEXT`); legacy `varchar(255)` installs are widened on upgrade so
  introspect does not fail with SQL Server “String or binary data would be truncated”
- default order when it is not the label (events by date, news newest first)
- which extra columns a list or search form shows (a province, a date)
- `tags` / relationship pickers on columns not named `tags`; tinymce on a text column with no HTML yet
- `mandatory` follows the database: hand-made metadata is usually laxer than its own `NOT NULL` columns
- **UI labels / `metadata_group`**: field labels are only `_` → space + capitalize; glued Italian names
  (`a_operatorimansionioperative`, `codicefiscale`) stay unreadable. There is no metadata display `label`
  column (SPA shows `table_name`; use `description` + `metadata_group`). Prefer a post-introspect AI pass
  (skill `enrich_metadata_catalog` + MCP tools, or `dev/scripts/enrich-metadata-pass1.py`) with `_limit=50`,
  multi-pass until join targets exist, and data sampling for join vs multijoin.

```bash
curl -sf -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"tables":["products"],"sample_rows":20}' \
  http://127.0.0.1:8080/api/introspect/preview
```
