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
- `sample_rows`: rows sampled for the data heuristics below (default 30, max 200)
- Tables already referenced by current metadata (`table_name` or `alias_table`) are **always skipped** (warning only; not returned as entities)

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
- select options are only the values already used, not the full domain (e.g. every country)
- default order when it is not the label (events by date, news newest first)
- which extra columns a list or search form shows (a province, a date)
- `tags` / relationship pickers on columns not named `tags`; tinymce on a text column with no HTML yet
- `mandatory` follows the database: hand-made metadata is usually laxer than its own `NOT NULL` columns

```bash
curl -sf -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"tables":["products"],"sample_rows":20}' \
  http://127.0.0.1:8080/api/introspect/preview
```
