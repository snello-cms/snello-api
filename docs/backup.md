# Business-data backup (ZIP)

Chunked **row** export/import. Does **not** export or apply metadata/DDL — use `POST /api/metadatas/export|import` for schema and field definitions. Import never runs `CREATE TABLE`.

Available on the **control** plane and **all-in-one** (`BackupServiceRs`).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/backup/data` | ZIP of all non-reserved business tables |
| `GET` | `/api/backup/data/{metadata_name}` | ZIP for one table |
| `POST` | `/api/backup/data` | multipart field `file` — import ZIP |
| `GET` / `POST` | `/api/backup/files` | not implemented |

Auth: same as other write APIs for `POST` (role `Admin|Manager|User`); `GET` follows `/api/*` GET policy.

## ZIP layout

```text
manifest.json
data/
  brands/0001.json
  kayaks/0001.json
  news/0001.json
  news/0002.json
  …
```

`manifest.json` fields:

- `kind`: must be `snello-backup-data`
- `version`, `created_at`, `page_size`
- `tables[]`: `{ name, chunks, rows, skipped?, reason? }`

Each `data/{table}/NNNN.json` is a JSON **array** of row objects (default page size **500**, configurable with `snello.backup.data.page-size`).

## Import behaviour

1. Unzip to a temp directory; require `manifest.json` with the expected `kind`.
2. For each table directory under `data/`:
   - resolve metadata (skip reserved names)
   - for each row: if key exists → **skip**; else → `create`
3. Response: `{ "imported": N, "skipped": M, "errors": [ … ] }`

Missing physical tables produce per-table errors (create the table with `GET /api/metadatas/{uuid}/create` first).

## Examples

```bash
# Export all
curl -sf -H "Authorization: Bearer $TOKEN" \
  -o snello-backup-data.zip \
  http://127.0.0.1:8080/api/backup/data

# Export one table
curl -sf -H "Authorization: Bearer $TOKEN" \
  -o kayaks.zip \
  http://127.0.0.1:8080/api/backup/data/kayaks

# Import
curl -sf -H "Authorization: Bearer $TOKEN" \
  -F "file=@snello-backup-data.zip;type=application/zip" \
  http://127.0.0.1:8080/api/backup/data
```

Seed files used in local all-in-one workflows: `dev/exports/metadatas-from-pg.json` + `dev/exports/snello-backup-data.zip` (see [All-in-one](all-in-one.md)).
