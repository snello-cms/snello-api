# Field Definitions

`FieldDefinition` entries describe metadata fields: UI input behavior, SQL mapping, search rules, and list visibility.

In code, the flat model is [`FieldDefinition`](../snello-core/src/main/java/io/snello/model/FieldDefinition.java). Datalist returns nested facets (`FieldModel`: `query` / `ui` / `ai`). Typed enums: `WidgetType`, `InputType`, `SearchOp`, `SemanticType`.

## Role in the System

Each field definition belongs to a metadata (`metadata_uuid`, `metadata_name`) and defines:

- how the field is rendered (`type`, `input_type`, `options`)
- how it is persisted in DB (`sql_type`, `sql_definition`, `default_value`)
- how it is used in search/list (`searchable`, `search_condition`, `show_in_list`)
- how it is ordered in the UI (`view_index`, `group_name`, `tab_name`)

## Main Fields

Identity and linking:

- `uuid`
- `metadata_uuid`
- `metadata_name`
- `name`
- `label`

Rendering/UI:

- `type`: input, button, select, date, radiobutton, checkbox, join, lookup, multijoin, multilookup, multiselect
- `input_type`: text, number, email, date, ecc.
- `options`: comma-separated values (for example select/radio)
- `group_name`: logical group in the page
- `tab_name`: wizard/page tab
- `input_disabled`: disables input in edit mode
- `view_index`: display order

SQL persistence:

- `table_key`: true if it is the table key field
- `sql_type`: SQL type
- `sql_definition`: additional SQL definition
- `default_value`: applied default value
- `pattern`: validation pattern/regex
- `function_def`: transformation function

Join and references:

- `join_table_name`
- `join_table_key`
- `join_table_select_fields`

Notes:

- `multiselect` is handled as a multijoin alias (many related values through the join table).

Search and list:

- `searchable`
- `search_condition`: es. `=`, `_contains`, `_gt`, `_lte`
- `search_field_name`: actual search parameter name
- `show_in_list`

## Schema sync on created tables

On a table that already exists, adding a field adds the column and renaming a field renames the column (data kept).
Deleting a field definition does **not** drop its column: the data stays in the table, unused. Drop it by hand if
it is really gone.

## Endpoint API

Admin CRUD (flat rows → `fielddefinitions` table):

- `GET /api/fielddefinitions`
- `GET /api/fielddefinitions/{uuid}`
- `POST /api/fielddefinitions`
- `PUT /api/fielddefinitions/{uuid}`
- `DELETE /api/fielddefinitions/{uuid}`

SPA / datalist (facet composition — preferred for UI):

- `GET /api/datalist/metadata/{name}/fielddefinitions` → `FieldModel[]` with nested `query` / `ui` / `ai`
- `GET /api/datalist/metadata/{name}/fields/ui` → identity + UI facet only
- `GET /api/datalist/metadata/{name}/fields/query` → query-plane projection only

## Example Payload (datalist facet)

```json
{
  "uuid": "...",
  "metadata_uuid": "...",
  "metadata_name": "brands",
  "name": "name",
  "query": {
    "sql_type": "varchar(255)",
    "table_key": false,
    "searchable": true,
    "search_condition": "_contains",
    "search_field_name": "name_contains"
  },
  "ui": {
    "label": "Name",
    "type": "input",
    "input_type": "text",
    "show_in_list": true,
    "view_index": 1
  },
  "ai": {
    "description": null
  }
}
```

## Best practice

- Keep `name` aligned with the DB column.
- Use unique and progressive `view_index` values to avoid ambiguous ordering.
- Enable `searchable` only for fields that are truly useful for filtering.
- If a field is a key (`table_key=true`), avoid uncontrolled edits from the UI.
