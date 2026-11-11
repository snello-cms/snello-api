# Schema introspect (existing tables → metadata)

Control plane + all-in-one. Reverse of [drafts](drafts.md): read physical tables and propose `metadatas` + `fielddefinitions` with `already_exist=true`. Never creates or alters tables.

Base path: `/api/introspect`

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/introspect/preview` | Derive rows; dry run |
| `POST` | `/api/introspect/apply` | Persist metadata + fields (Admin / Manager) |

Body (`IntrospectRequest`):

```json
{
  "tables": ["products", "categories"],
  "sample_rows": 30,
  "metadata_group": "data"
}
```

- `tables`: optional allow-list; omit/empty = every non-reserved user table **not** already referenced by metadata
- `sample_rows`: rows sampled for HTML / long-string heuristics (default 30, max 200)
- Tables already referenced by current metadata (`table_name` or `alias_table`) are **always skipped** (warning only; not returned as entities)

### Heuristics

- **Primary key**: JDBC PK → `uuid` / `autoincrement` / `userdefined` (generated-style keys are not declared as fields)
- **Scalars**: SQL type → `semantic_type` via `FieldUiDerivation`; wide varchar / `text` → textarea; HTML tags in samples → tinymce
- **Join**: JDBC FK to a known table, or `*_id` / `*_uuid` whose prefix matches a known table → `type=join` + `join_table_name` / `join_table_key`. Otherwise the column stays a plain key (fixable in the SPA)
- **Multijoin**: snello-style bridge `a_b` with `a_id` + `b_id` is skipped as an entity and attached as `multijoin` on one side when both ends are available

```bash
curl -sf -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"tables":["products"],"sample_rows":20}' \
  http://127.0.0.1:8080/api/introspect/preview
```
