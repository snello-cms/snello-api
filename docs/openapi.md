# OpenAPI

Every Quarkus plane (`snello-control`, `snello-query`, `snello-all-in-one`) exposes a generated **OpenAPI 3** document for all registered JAX-RS resources (SmallRye OpenAPI).

## Endpoints

| Method | Path | Format | Auth |
|---|---|---|---|
| `GET` | `/openapi` | **JSON** | public |
| `GET` | `/q/openapi` | YAML by default; JSON with `Accept: application/json` or `?format=json` | public |

```bash
curl -s http://127.0.0.1:8080/openapi | jq '.info'
curl -s http://127.0.0.1:8080/openapi | jq '.paths | keys | length'
```

## What is included

- All JAX-RS `@Path` resources active on **this** runtime (query plane omits control-only RS when `snello.control-plane.enabled=false`).
- Generic data API paths (`/api/{table}`, …) from `ApiServiceRs` — not one path per metadata name.
- Title/version from `quarkus.smallrye-openapi.info-*` (defaults in `snello-core`).

Implementation: [`OpenApiServiceRs`](../snello-core/src/main/java/io/snello/service/rs/OpenApiServiceRs.java) returns the document via `OpenApiDocumentService` as `application/json`.

## Data API per table (generated from the metadata)

The document above knows only the generic `/api/{table}`. For someone building against **your** tables there is a
second document, generated on request from the metadata catalog:

| Method | Path | What |
|---|---|---|
| `GET` | `/openapi/data/{table}` | one table (the layouts **API** panel links to it) |
| `GET` | `/openapi/data?group=Acquisti` | the tables of one `metadata_group` |
| `GET` | `/openapi/data?tables=a,b` | a chosen set; no parameter = every table |
| `GET` | `/openapi/data/tables` | the table names |

Per table: `GET`/`POST /api/{table}`, `GET`/`PUT`/`DELETE /api/{table}/{key}`, and `GET /api/{table}/{key}/{target}`
for each many-to-many field. Writes are listed on the control plane only.

- **Row schema** from the field types: checkbox → `boolean`; date / datetime / time → `string` + `format`; number /
  decimal (or an int / decimal SQL type) → `integer` / `number`; select / radio → `enum` of its options; email / url
  → `format`; join → the linked row's key, with its `GET /api/{target}/{key}`. Mandatory fields are `required`;
  generated keys (uuid / slug / autoincrement) are `readOnly`.
- **List filters per field, by type** (only the operators `ParamUtils` actually parses):
  text `=` `_contains` `_ncontains` `_containss` `_icontains` `_rlike` `_llike` `_in` `_nn` `_inn` `_ie` `_nie`;
  numbers / dates / times `=` `_lt` `_lte` `_gt` `_gte` `_in` `_nn` `_inn`; boolean `=` `_nn` `_inn`;
  join keys `=` `_in` `_nn` `_inn`. Plus `_limit`, `_start`, `_sort`, `select_fields`, the `x-total-count` header,
  and `or1.` / `and1.` groups. (`_ne` is declared in `ParamUtils` but not parsed, so it is not offered.)
- **Security** as enforced: reads public unless the table is *api protected* (then a bearer token; with a
  *username field* a plain user only sees their own rows); writes need a bearer token with a role of `role-policy1`
  (Admin, Manager, User by default; read from the config). Schemes: `bearerAuth`, plus `oidc` when
  `quarkus.oidc.auth-server-url` is set.
- `servers` is `/`, so Swagger UI / Postman resolve it against the host the document was loaded from.

Implementation: [`DataOpenApiService`](../snello-core/src/main/java/io/snello/service/DataOpenApiService.java),
[`DataOpenApiServiceRs`](../snello-core/src/main/java/io/snello/service/rs/DataOpenApiServiceRs.java). Test:
`DataOpenApiTest` calls the real list endpoint with every documented filter, and writes the documents to
`snello-control-app/target/` (checked with `openapi-spec-validator`).
