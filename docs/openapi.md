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
