# Layouts

SPA page layouts for a metadata entity: list, edit, and view. Rows live in `layouts`
(`/api/layouts`; writes need Admin or Manager). This is a dedicated system resource (like
[print templates](print-templates.md)), not a form driven by `fielddefinitions`.

## Columns

| Column | Type | Notes |
|---|---|---|
| `uuid` | varchar(255) PK | Server-generated |
| `name` | varchar(255) NOT NULL | Library label |
| `page_type` | varchar(10) NOT NULL | `list` \| `edit` \| `view` |
| `metadata_uuid` | varchar(255) NOT NULL | `metadatas.uuid` |
| `metadata_name` | varchar(255) NOT NULL | `metadatas.table_name` (denormalized, like conditions) |
| `json_data` | long text | Page body only |
| `updated_at` | varchar(32) NOT NULL | ISO-8601, set on every write |

Non-unique index on `(metadata_uuid, page_type)` for sibling lookups (view of a list, edit of a view).
No uniqueness on that pair: clones may share the same metadata + page type; only `uuid` is unique.

## `json_data`

Stores only the body (plus a version). Do not repeat name, page type, or metadata inside the JSON.

```json
{ "version": 1, "kind": "list", "columns": [] }
```

```json
{ "version": 1, "kind": "wizard", "pages": [] }
```

- `page_type=list` → `kind` must be `list`
- `page_type=edit` or `view` → `kind` must be `wizard`

Writes reject a missing metadata, invalid `page_type`, or a `kind` that does not match `page_type`.
`metadata_name` is always filled from the referenced metadata.

## API

- `GET /api/layouts`
- `GET /api/layouts/{uuid}`
- `POST /api/layouts`
- `PUT /api/layouts/{uuid}`
- `DELETE /api/layouts/{uuid}`

Default list sort: `updated_at desc`. Filter with the usual query params (`name_contains`, `metadata_name`, `page_type`, …).

Config export/import flag: `"layouts": true` under the metadata transfer `config` section.
