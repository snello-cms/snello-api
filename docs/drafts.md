# Entity drafts (AI / schema apply)

Control plane + all-in-one. Drafts let you validate and apply a structured entity definition (fields + optional query/UI/AI facets) without hand-writing every flat `metadatas` / `fielddefinitions` column.

Base path: `/api/drafts`

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/drafts/validate` | Validate + derive UI/SQL facets; returns errors, warnings, preview DDL |
| `POST` | `/api/drafts/apply?create_table=false\|true` | Persist metadata + fields; optionally run `CREATE TABLE` |

Body shape (`EntityDraft`): `table_name`, optional `query` (`table_key`, `table_key_type`, `table_key_addition`, …), `ai`, and `fields[]` with nested `query` / `ui` / `ai` facets.

### Slug keys

When `query.table_key_type` is `slug`, set `query.table_key_addition` to the source field name. On `CREATE TABLE`, the key `VARCHAR` is sized to that field when it is wider than 50 (see [Metadata — key types](metadata.md#table-key-types)).

```bash
curl -sf -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"table_name":"articles","ai":{"description":"News articles"},"query":{"table_key_type":"slug","table_key_addition":"title","api_protected":false},"fields":[{"name":"title","ai":{"semantic_type":"text","description":"Title"}}]}' \
  http://127.0.0.1:8080/api/drafts/validate
```
