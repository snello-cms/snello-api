# Snello API

![Snello Logo](docs/images/snello-logo.svg)

[![Built with Maven](https://img.shields.io/badge/Built%20with-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Quarkus](https://img.shields.io/badge/Quarkus-4.0.0.Beta3-4695EB?logo=quarkus&logoColor=white)](https://quarkus.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

Snello is a metadata-driven headless CMS API.

It lets you model entities, fields, and query behavior through metadata, then expose REST endpoints without writing one controller per business table.

## Why Snello

- Metadata-first API design
- Dynamic table and field management
- Generic REST layer for CRUD and query operations
- Generated OpenAPI 3 JSON at `GET /openapi` (all JAX-RS methods of the running plane)
- Pluggable document storage (filesystem, S3-compatible)
- Built-in support for derived media generation (formats and webp)

## Modules (6.x alpha)

```text
snello-parent
  snello-core               shared domain / JDBC / services
  snello-control-app     →  snellocms/snello-control
  snello-query-app       →  snellocms/snello-query
  snello-mcp-app         →  snellocms/snello-mcp
  snello-all-in-one-app  →  snellocms/snello-all-in-one
```

DB-facing JVM images use Quarkus **mutable-jar** and Keycloak-style **re-augmentation**: set `SNELLO_DB_KIND=postgresql|mysql|h2` so the entrypoint runs `snello-optimize.sh` once, then starts.

## Branches / versions

| Branch | App version | Quarkus | Docker tags |
|---|---|---|---|
| `main` | `5.1.1` | 3.40.1.1 | `:5.1.1`, `:latest` |
| `6.0.0-alpha` | `6.0.1-alpha` | 4.0.0.Beta3 | `:6.0.1-alpha`, `:alpha` (no `:latest`) |

Alpha patch bumps (`6.0.x-alpha`) advance the Maven / Docker app tag on each meaningful drop while the git branch stays `6.0.0-alpha`. Target after feature freeze: `6.1.0-alpha` (or beta).

Architecture notes for 6.x (facets, planes, roadmap status): [ARCHITECTURE_REENGINEERING.md](ARCHITECTURE_REENGINEERING.md) · [IT](ARCHITECTURE_REENGINEERING.it.md).

## Planes & facets (6.x)

Same flat DB tables (`metadatas` / `fielddefinitions`); concerns split in Java and in REST contracts.

| Plane | Image | Surface |
|---|---|---|
| **Control** | `snello-control` (`:8080`) | Full API: data CRUD, metadata/DDL, documents, auth, AI, drafts |
| **Query** | `snello-query` (`:8081`) | Read-only GETs on declared metadata / select queries; no admin RS, no writes, no AI |
| **MCP** | `snello-mcp` | AI / tools plane (shell module; protocol TBD) |
| **All-in-one** | `snello-all-in-one` (`:8080`) | Full control API + **H2** + filesystem docs; **no SPA**; **external SSO** (OIDC) |

**Query plane JDBC:** connections are forced read-only (`quarkus.datasource.jdbc.additional-jdbc-properties.readOnly=true` + `ReadOnlyConnectionInterceptor`). Point `QUARKUS_DATASOURCE_JDBC_URL` at a replica when you have one. Metadata snapshots refresh on an interval (`SNELLO_METADATA_REFRESH_INTERVAL`, default `30s` / compose `10s`).

**Catalog / facets:** `EntityCatalog` exposes `queryView` / `uiView` / `aiView`. List/fetch use the query view; the SPA should prefer datalist facet endpoints (below) over flat `/api/fielddefinitions`. Typed enums (`WidgetType`, `InputType`, `KeyType`, `SearchOp`, `SemanticType`) back widget/SQL mapping. SQL list/count/CRUD fragments go through a shared `SqlQueryBuilder` (thin H2/MySQL/PostgreSQL facades).

Open follow-ups: optional embedded SPA in all-in-one, full MCP protocol, SPA facet consumers, optional PG identifier quoting.

## Documentation

- [Documentation Index](docs/index.md)
- [All-in-one](docs/all-in-one.md) — Docker / `docker-compose-aio.yaml` / `docker-compose-aio-sso.yaml`
- [OpenAPI](docs/openapi.md) · [Backup](docs/backup.md) · [Drafts](docs/drafts.md)
- [Metadata](docs/metadata.md)
- [Field Definitions](docs/field-definitions.md)
- [SelectQuery](docs/select-query.md)
- [Document Management](docs/document-management.md)
- [REST Query Params](docs/rest-query-params.md)
- [AI Agent](docs/ai-agent.md)
- [AI Tools Schema](docs/ai-tools-schema.md)

## Quick Start (dev)

```bash
./mvnw -pl snello-control-app -am quarkus:dev
```

Quarkus Compose Dev Services starts dependencies from `compose-devservices.yml` (PostgreSQL, Keycloak, MinIO/Silo).

Stop local dependencies:

```bash
docker compose -f compose-devservices.yml down
```

## Docker images

| Image | Role |
|---|---|
| `snellocms/snello-control` | control plane (also aliased as `snello-api` during alpha) |
| `snellocms/snello-query` | query plane |
| `snellocms/snello-mcp` | MCP / AI tools plane |
| `snellocms/snello-all-in-one` | single container: H2 + full API, no SPA, external OIDC |

```bash
# Build all modules
./mvnw -DskipTests package

# Local control image
docker build -f snello-control-app/src/main/docker/Dockerfile.jvm \
  -t snellocms/snello-control:local snello-control-app
docker run --rm -p 8080:8080 -e SNELLO_DB_KIND=postgresql snellocms/snello-control:local

# Re-augment only (Keycloak-style), without starting:
./snello-control-app/src/main/scripts/snello-optimize.sh \
  snello-control-app/target/quarkus-app/quarkus-run.jar
```

### All-in-one (H2, no SPA, external SSO)

Single JVM image with the full control API, embedded **H2** (file under `/data` by default), and **filesystem** document storage. No Postgres, MinIO, or Keycloak in the container — point OIDC at your issuer.

```bash
./mvnw -pl snello-all-in-one-app -am package -DskipTests
docker build -f snello-all-in-one-app/src/main/docker/Dockerfile.jvm \
  -t snellocms/snello-all-in-one:local snello-all-in-one-app

docker run --rm -p 8080:8080 -v snello-aio:/data \
  -e QUARKUS_OIDC_AUTH_SERVER_URL=https://your-sso/realms/snello \
  -e QUARKUS_OIDC_CLIENT_ID=snello-api \
  snellocms/snello-all-in-one:local
```

Or with Compose — see **[docs/all-in-one.md](docs/all-in-one.md)** for the full guide:

```bash
# External SSO + persistent H2 volume
export QUARKUS_OIDC_AUTH_SERVER_URL=https://your-sso/realms/snello
docker compose -f docker-compose-aio.yaml up -d --build

# Bundled Keycloak (dev-file H2) + all-in-one (file H2)
docker compose -f docker-compose-aio-sso.yaml up -d --build
```

Useful env vars:

| Variable | Default / notes |
|---|---|
| `QUARKUS_OIDC_AUTH_SERVER_URL` | **required** — OIDC realm / issuer URL |
| `QUARKUS_OIDC_CLIENT_ID` | `snello-api` |
| `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH` | `resource_access/snello-api/roles` |
| `SNELLO_JDBC_URL` | file H2 at `/data/snello`; override with `jdbc:h2:mem:…` for ephemeral |
| `SNELLO_FS_BASE_PATH` | `/data/files` |
| `SNELLO_KEYCLOAK_ADMIN_*` | optional admin bootstrap against the same SSO |
| `SNELLO_AITYPE` | optional; `ai-gemini` (compose default) or `openai` |
| `OPENAI_API_KEY` / `GEMINI_API_KEY` | optional; AG-UI agent keys |
| `GEMINI_MODEL_QUICK` / `GEMINI_MODEL_PRO` | optional Gemini model ids |
| `SNELLO_AI_MCP_BASE_URL` | optional base URL for MCP HTTP tools |

## Compose files

| File | Purpose |
|---|---|
| `compose-devservices.yml` | Quarkus `%dev` dependencies |
| `compose-test.yml` | Quarkus test dependencies (PostgreSQL) |
| `compose.yml` | Full stack: deps + `snello-control` + `snello-query` (`:alpha`) |
| `docker-compose-aio.yaml` | **All-in-one** + external SSO + volume `/data` (H2) — [docs](docs/all-in-one.md) |
| `docker-compose-aio-sso.yaml` | **All-in-one** + Keycloak (`dev-file` H2) + volume `/data` — [docs](docs/all-in-one.md) |
| `compose-allinone.yml` | Legacy external-SSO layout (prefer `docker-compose-aio.yaml`) |
| `compose-allinone-local.yml` | Local Keycloak on `:18180` / API `:18080` |
| `compose-allinone-test.yml` | All-in-one IT on `:18080` (OIDC off; dump import tests) |

```bash
# Full local stack (control + query + deps)
./mvnw package -DskipTests
docker compose up -d --build
```

Pinned service images (current):

- `postgres:18.6`
- `quay.io/keycloak/keycloak:26.8.2`
- `pgsty/silo:RELEASE.2026-09-16T00-00-00Z` (MinIO-compatible; official `minio/minio` is no longer publicly pullable)
- `amazon/aws-cli:2.31.38` (bucket bootstrap)

DB seed / restore scripts live under `dev/db/` (mounted as `/seed` by `compose.yml`; restore runs only on first Postgres volume init). Keycloak realm imports under `dev/keycloak/`.

Local stack ports after `docker compose up -d --build`:

| Service | Port |
|---|---|
| snello-control | `8080` |
| snello-query | `8081` |
| PostgreSQL | `5432` |
| Keycloak | `8180` |
| MinIO / Silo | `9000` / `9001` |

To re-run DB restore from scratch: `docker compose down -v` then `up --build` again.

## Control plane APIs (`snello-control`)

With `snello.control-plane.enabled=true`, control registers the full surface: data CRUD + metadata/DDL + documents + auth + AI. Auth typical: GET/OPTIONS open on `/api/*`; writes need role `Admin|Manager|User`; `/api/auth*` Admin-only.

### OpenAPI (`GET /openapi`)

SmallRye OpenAPI scans every JAX-RS resource registered on the **running plane** (control, query, or all-in-one) and exposes it as an OpenAPI 3 document.

| Method | Path | Format | Auth |
|---|---|---|---|
| GET | `/openapi` | **JSON** (preferred) | public (`permit`) |
| GET | `/q/openapi` | YAML by default; JSON with `Accept: application/json` or `?format=json` | public |

```bash
# Control / all-in-one
curl -s http://127.0.0.1:8080/openapi | jq '.paths | keys | length'
curl -s http://127.0.0.1:8080/openapi | jq '.paths | keys[]' | head

# Query plane
curl -s http://127.0.0.1:8081/openapi | jq '.info'
```

Notes:

- Content reflects **this** runtime: query plane documents only the RS that are active there (no metadata/DDL/AI admin when `snello.control-plane.enabled=false`).
- Dynamic business tables (`GET /api/{table}`) appear as the generic `ApiServiceRs` paths, not as one path per metadata name.
- Title/version come from `quarkus.smallrye-openapi.info-*` (shared defaults in `snello-core`).
- Implementation: `OpenApiServiceRs` returns the generated document via `OpenApiDocumentService` as `application/json`.

### Generic data (metadata-driven tables)

| Method | Path | Role |
|---|---|---|
| GET | `/api/{table}` | list (+ sort/limit/start + query filters) |
| GET | `/api/{table}/{uuid}` | fetch |
| GET | `/api/{table}/{uuid}/{path…}` | nested / join path |
| POST | `/api/{table}` | create |
| PUT | `/api/{table}/{uuid}` | update |
| DELETE | `/api/{table}/{uuid}` | delete |

### Query plane APIs (`snello-query`)

Only GET variants of `/api/{table…}` for **declared** metadata / select queries. Undeclared system tables (`metadatas`, `fielddefinitions`, …) return `404`. Mutations return `405`. Admin/metadata/AI RS are not registered.

### Metadata & schema

| Path | Purpose |
|---|---|
| `/api/metadatas` | CRUD metadatas (flat admin rows) |
| `GET /api/metadatas/{uuid}/create\|delete\|truncate` | DDL table actions |
| `GET /api/metadatas/groups` | metadata groups |
| `POST /api/metadatas/export\|import` | export/import |
| `/api/fielddefinitions` | CRUD field definitions (**flat** rows → DB) |
| `/api/conditions` | CRUD conditions |
| `/api/selectqueries` | CRUD select queries |
| `/api/actions` | CRUD actions |
| `/api/datalist/names` | entity names for the SPA |
| `/api/datalist/metadata/{name}` | metadata as **facets** (`identity` / `query` / `ui` / `ai`) |
| `/api/datalist/metadata/{name}/fielddefinitions` | fields as **FieldModel** (nested facets) |
| `/api/datalist/metadata/{name}/fields/ui` | UI facet only |
| `/api/datalist/metadata/{name}/fields/query` | query facet only |
| `/api/datalist/metadata/{name}/fields/ai` | AI facet only |
| `/api/datalist/metadata/{name}/ui` | full `EntityUiView` |
| `/api/datalist/metadata/{name}/ai` | full `EntityAiView` |
| `/api/datalist/metadata/{name}/condition` | conditions for an entity |
| `POST /api/drafts/validate` | validate AI/schema draft |
| `POST /api/drafts/apply` | apply draft |

### How to read field definitions

Preferred for the SPA (by **table name** / `table_name`), on control (`:8080`):

```http
GET http://localhost:8080/api/datalist/metadata/kayaks/fielddefinitions
```

Returns `FieldModel[]` with nested facets, for example:

```json
{
  "uuid": "...",
  "metadata_uuid": "...",
  "metadata_name": "kayaks",
  "name": "category",
  "query": { "searchable": true, "search_field_name": "category", "table_key": false },
  "ui": { "label": "category", "type": "select", "options": "single,double", "show_in_list": true },
  "ai": { "description": null }
}
```

Single-concern field projections:

```http
GET /api/datalist/metadata/kayaks/fields/ui
GET /api/datalist/metadata/kayaks/fields/query
GET /api/datalist/metadata/kayaks/fields/ai
```

Entity-level catalog views (`EntityUiView` / `EntityAiView`):

```http
GET /api/datalist/metadata/kayaks/ui
GET /api/datalist/metadata/kayaks/ai
```

Metadata itself as facets:

```http
GET /api/datalist/metadata/kayaks
```

→ `{ "identity": { "table_name": "kayaks", ... }, "query": {...}, "ui": {...}, "ai": {...} }`

Flat admin/CRUD (still used for writes to `fielddefinitions`):

```http
GET /api/fielddefinitions?metadata_name=kayaks
GET /api/fielddefinitions/{uuid}
```

List available entity names: `GET /api/datalist/names`.

Storage stays a single flat `fielddefinitions` table; the split is in the **model / datalist responses** (`query` vs `ui` vs `ai`), not in separate DB tables.

### Documents & media

| Path | Purpose |
|---|---|
| `/api/documents` | list/CRUD documents, upload |
| `/api/documents/{uuid}/download`, `/webp`, `/download/{name}`, `/video/{name}` | download / derivatives |
| `/api/documents/cache/...` | cached document surface |

### Auth (Keycloak admin)

| Path | Purpose |
|---|---|
| `GET/POST /api/auth/users`, `PUT /api/auth/users/{id}` | users |
| `GET /api/auth/users/{id}/groups` | user groups |
| `GET /api/auth/groups`, `.../groups/{id}/users` | groups |
| `POST /api/auth/createGroups` | bootstrap groups |

### AI (AG-UI)

| Method / path | Purpose |
|---|---|
| `WS /agui` | AG-UI WebSocket agent (OIDC) |
| `/api/ai/history`, `/sessions`, `/feedback` | Conversation history |
| `/api/ai/me/projects`, `/card-catalog` | Caller projects / cards |
| `/api/ai/projects`, `/skills`, `/mcp-tools`, `/cards`, `/service-accounts` | Admin CRUD |
| `/api/ai_tools` | Dynamic SQL tools |

### Backup (business data ZIP)

Chunked row export/import. **Does not** touch schema — use `POST /api/metadatas/export|import` for metadata/fields. Import does **not** create tables (DDL must already exist).

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/backup/data` | ZIP of all business tables (pages of 500 rows → `data/{table}/NNNN.json` + `manifest.json`) |
| GET | `/api/backup/data/{metadata_name}` | ZIP for a single table |
| POST | `/api/backup/data` | multipart `file` ZIP → insert rows (`create` if key missing, skip if exists) |
| GET/POST | `/api/backup/files` | not implemented yet |

ZIP layout: `manifest.json` (`kind=snello-backup-data`) + `data/{table_name}/0001.json` …

Seed exports used by local all-in-one + Keycloak (`compose-allinone-local.yml`): `dev/exports/metadatas-from-pg.json` + `dev/exports/snello-backup-data.zip`, imported with `./dev/scripts/aio-import-from-exports.sh`.

### Slug primary keys

When `table_key_type=slug`, CREATE TABLE sizes the key column to the **slug source field** (`table_key_addition`) if that field is wider than the default `VARCHAR(50)` (e.g. `input`/`text` → `VARCHAR(200)`, select/join → `VARCHAR(2048)`, or `VARCHAR(n)` from `sql_definition`). Same rule on PostgreSQL, H2, and MySQL (`ColumnSpecs.resolveKeyVarcharLength` / dialect `keyColumnSql`).

### Other admin

| Path | Purpose |
|---|---|
| `/api/links` | CRUD CMS links |
| `GET /api/p/{table}/{uuid}` | template/pages |

More detail: [Metadata](docs/metadata.md), [Field Definitions](docs/field-definitions.md), [REST Query Params](docs/rest-query-params.md).

## Testing

```bash
./mvnw -pl snello-control-app -am test
./mvnw -pl snello-control-app -Dtest=io.snello.test.metadata.ProgramsMetadataCreationTest test
./mvnw -pl snello-control-app -Dtest=io.snello.test.openapi.OpenApiJsonTest test
```

`compose-test.yml` maps Postgres to host port **5433** so tests can run while the full stack occupies `5432`.

### All-in-one dump → export → import IT

Plan: restore the `dev/db` dump → export all metadatas as JSON (`POST /api/metadatas/export` with `{"all":true}`) → import that JSON into a Docker all-in-one → assert datalist/facets.

```bash
# One-shot (regenerates fixture if missing, starts compose-allinone-test, runs Failsafe)
./dev/scripts/run-aio-import-tests.sh

# Refresh the committed fixture from the dump
REGENERATE_EXPORT=1 ./dev/scripts/run-aio-import-tests.sh

# Or only regenerate JSON:
./dev/scripts/generate-metadatas-export.sh
```

| Piece | Role |
|---|---|
| `compose-test.yml` + dump | source DB for export |
| `dev/scripts/generate-metadatas-export.sh` | boots control against dump, writes `metadatas-from-dump.json` |
| `compose-allinone-test.yml` | all-in-one on `:18080` (OIDC off for deterministic IT) |
| `AllInOneDumpImportIT` | RestAssured black-box import + kayaks facet checks |

Fixture path: `snello-all-in-one-app/src/test/resources/fixtures/metadatas-from-dump.json`.

## AI Agent

See [AI Agent](docs/ai-agent.md).
