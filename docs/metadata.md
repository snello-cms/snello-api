# Metadata

`Metadata` describe a logical resource in the system (for example `brands`, `users`, `orders`) and define how SNELLO must manage it in terms of API behavior, queries, and data structure.

## Role in the System

A metadata entry represents the configuration of a table/entity:

- table name (`table_name`)
- primary key (`table_key`)
- fields shown in list or search
- conditions and sorting
- details used to create or manage the table

In code, the flat model is [`Metadata`](../snello-core/src/main/java/io/snello/model/Metadata.java); REST also exposes facet views via datalist (`identity` / `query` / `ui` / `ai`).

## Main Fields

- `uuid`: unique metadata identifier
- `table_name`: table/resource name (required)
- `table_key`: table key field (required)
- `table_key_type`: key type (`uuid` | `slug` | `autoincrement` | `userdefined`) — see [Table key types](#table-key-types)
- `table_key_addition`: for `slug`, the source field name the slug is built from
- `description`: functional description (also used by AI tools when `ai_profile` allows)
- `select_fields`: field list used in select queries
- `search_fields`: field list used in search
- `order_by`: default ordering
- `creation_query`: optional raw SQL table creation query (otherwise derived from fields)
- `tab_groups`: tab/wizard group definition
- `icon`: icon associated with the resource
- `already_exist`: true if the table already exists and is not created by SNELLO
- `created`: true if the table was created by the system
- `api_protected`: enables API protection for the resource
- `username_field`: user ownership/authorization field
- `ai_profile`: JSON profile (description, synonyms, `tool_visible`, …)
- `fields`: linked `FieldDefinition` list
- `conditions`: linked condition list

## Table key types

| `table_key_type` | CREATE TABLE key column | Notes |
|---|---|---|
| `uuid` | `VARCHAR(50) NOT NULL` | random UUID on create |
| `slug` | `VARCHAR(N)` sized to source field | `table_key_addition` must name the source field; `N` ≥ 50 and grows with the field (e.g. text → 200) |
| `autoincrement` | dialect serial / identity | |
| `userdefined` | no generated key column | key is one of the declared fields (`table_key` flag) |

Slug sizing is shared across PostgreSQL, H2, and MySQL (`ColumnSpecs.resolveKeyVarcharLength` / `SlugKeySizes` on 5.1.x). See also [Drafts](drafts.md) and [Introspect](introspect.md) for existing tables (`already_exist`).

## Ownership Protection: `api_protected` and `username_field`

When `api_protected=true`, access to resource data is owner-aware.

- `username_field` is the column/property used to store the owner username.
- The owner value is taken from the token principal (`securityContext.getUserPrincipal().getName()`).
- For non-admin and non-manager users, this principal drives data visibility and write ownership.

Behavior implemented in API resource layer (`ApiServiceRs`):

1. `POST /api/{table}`
- For protected metadata, the server sets `username_field` to the authenticated principal.
- This guarantees that the stored owner matches the token identity, even if the JSON payload contains a different value.

2. `PUT /api/{table}/{uuid}`
- For protected metadata, the server again forces `username_field` to the authenticated principal for non-admin/manager users.

3. `GET /api/{table}`
- For protected metadata, non-admin/manager users are filtered by owner (`username_field = principal`).

4. `GET /api/{table}/{uuid}` and `DELETE /api/{table}/{uuid}`
- For protected metadata, non-admin/manager users can fetch/delete only records where owner equals principal.

5. Admin/Manager roles
- Admin/Manager users are not constrained by owner filtering in these flows.

Practical rule:

- For protected metadata, each record owner must match the token principal.
- If a client sends a different owner in JSON, the API normalizes it to the principal for non-admin/manager users.
- Therefore, clients should send `username_field` consistent with token identity to keep payloads explicit and aligned with persisted data.

### Protected Metadata Example

Example metadata configuration for an owner-protected resource:

```json
{
  "table_name": "user_actions",
  "table_key": "uuid",
  "description": "Track user interactions",
  "api_protected": true,
  "username_field": "action_username",
  "select_fields": "uuid,action_username,action_name,data_table_name,data_table_key,creation_date",
  "search_fields": "action_username,data_table_name,data_table_key"
}
```

### Request vs Persisted Value

Assume authenticated principal is `snello@snello.io`.

| Scenario | JSON request value for `action_username` | Persisted value (`action_username`) |
|---|---|---|
| Non-admin/manager user, matching value | `snello@snello.io` | `snello@snello.io` |
| Non-admin/manager user, different value | `other.user@example.com` | `snello@snello.io` |
| Admin/Manager user | `other.user@example.com` | not forced by owner filter in this layer |

For non-admin/manager users, owner is enforced from the token principal in protected metadata flows.

## Endpoint API

Metadata base path:

- `GET /api/metadatas`
- `GET /api/metadatas/{uuid}`
- `POST /api/metadatas`
- `PUT /api/metadatas/{uuid}`
- `DELETE /api/metadatas/{uuid}`

Additional table actions (DDL: `POST`, role **Admin** only — every `GET` on `/api/*` is public, so these used to
let anyone create, drop or empty tables):

- `POST /api/metadatas/{uuid}/create`
- `POST /api/metadatas/{uuid}/delete` (drops the table)
- `POST /api/metadatas/{uuid}/truncate`

Writes to the configuration resources (`metadatas`, `fielddefinitions`, `conditions`, `actions`, `selectqueries`,
`links`, AI projects / skills / tools / cards / service accounts) require **Admin** or **Manager**; reading them is
open. Data rows (`/api/{table}`) keep the `User` role and row-level security.

Import sets `created` from whether the table exists in the target database (not from the exported value).

## Row validation

`POST` / `PUT /api/{table}` check the row against its field definitions: `mandatory` fields must be present and not
blank on create (on update they may be omitted, not blanked), and a `select` value must be one of its `options`.
Violations, values of the wrong type (e.g. an invalid date) and constraint errors answer `400`; a duplicate key `409`.

Related datalist endpoints:

- `GET /api/datalist/metadata/{name}` → flat `Metadata` (Snello 5 / SPA contract)
- `GET /api/datalist/metadata/{name}/fielddefinitions` → flat `FieldDefinition[]`
- `GET /api/datalist/metadata/{name}/model` → `EntityModel` facets (`identity` / `query` / `ui` / `ai`)
- `GET /api/datalist/metadata/{name}/fieldmodels` → `FieldModel[]` (nested facets)
- `GET /api/datalist/metadata/{name}/fields/ui`
- `GET /api/datalist/metadata/{name}/fields/query`
- `GET /api/datalist/metadata/{name}/fields/ai`
- `GET /api/datalist/metadata/{name}/ui` / `…/ai`

Export / import (metadata + fielddefinitions, not row data):

- `POST /api/metadatas/export`
- `POST /api/metadatas/import`

Admin CRUD on `/api/metadatas` remains flat (table columns). For row backup see [Backup](backup.md).

## Example Payload (datalist facet)

```json
{
  "identity": {
    "uuid": "...",
    "table_name": "brands",
    "already_exist": false,
    "created": true
  },
  "query": {
    "table_key_type": "varchar(255)",
    "select_fields": "uuid,name",
    "order_by": "name asc",
    "api_protected": false
  },
  "ui": {
    "icon": "fa fa-tag",
    "search_fields": "name"
  },
  "ai": {
    "description": "Brand catalog"
  }
}
```

## Guidelines

- Avoid reserved names for `table_name` (service-side validation).
- Always define `table_key` consistently with field definitions.
- Use `already_exist=true` when the table is managed externally.
- Use `created=true` only after actual table creation (`/create`).
