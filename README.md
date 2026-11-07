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
- Pluggable document storage (filesystem, S3-compatible)
- Built-in support for derived media generation (formats and webp)

## Modules (6.x alpha)

```text
snello-parent
  snello-core            shared domain / JDBC / services
  snello-control-app  →  snellocms/snello-control
  snello-query-app    →  snellocms/snello-query
  snello-mcp-app      →  snellocms/snello-mcp
```

DB-facing JVM images use Quarkus **mutable-jar** and Keycloak-style **re-augmentation**: set `SNELLO_DB_KIND=postgresql|mysql|h2` so the entrypoint runs `snello-optimize.sh` once, then starts.

## Branches / versions

| Branch | App version | Quarkus | Docker tags |
|---|---|---|---|
| `main` | `5.1.0` | 3.40.1.1 | `:5.1.0`, `:latest` |
| `6.0.0-alpha` | `6.0.0-alpha` | 4.0.0.Beta3 | `:6.0.0-alpha`, `:alpha` (no `:latest`) |

Architecture proposal for 6.x: [ARCHITECTURE_REENGINEERING.md](ARCHITECTURE_REENGINEERING.md).

## Documentation

- [Documentation Index](docs/index.md)
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

## Compose files

| File | Purpose |
|---|---|
| `compose-devservices.yml` | Quarkus `%dev` dependencies |
| `compose-test.yml` | Quarkus test dependencies (PostgreSQL) |
| `compose.yml` | Full stack + `snellocms/snello-control:alpha` |

```bash
# Full local stack (control image + deps)
docker compose up -d
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

### Generic data (metadata-driven tables)

| Method | Path | Role |
|---|---|---|
| GET | `/api/{table}` | list (+ sort/limit/start + query filters) |
| GET | `/api/{table}/{uuid}` | fetch |
| GET | `/api/{table}/{uuid}/{path…}` | nested / join path |
| POST | `/api/{table}` | create |
| PUT | `/api/{table}/{uuid}` | update |
| DELETE | `/api/{table}/{uuid}` | delete |

On **snello-query** only the GET variants of `/api/{table…}` remain registered; admin RS are not present.

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
| `/api/datalist/metadata/{name}/condition` | conditions for an entity |
| `POST /api/drafts/validate` | validate AI/schema draft |
| `POST /api/drafts/apply` | apply draft |

### How to read field definitions

Preferred for the SPA (by **table name** / `table_name`):

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

Single-concern projections:

```http
GET /api/datalist/metadata/kayaks/fields/ui
GET /api/datalist/metadata/kayaks/fields/query
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

### AI / chat

| Path | Purpose |
|---|---|
| `POST /api/chat` | LangChain4j chatbot |
| `/api/chat_interactions` | CRUD chat interactions |
| `/api/ai_tools` | CRUD AI tools |

### Other admin

| Path | Purpose |
|---|---|
| `/api/links` | CRUD CMS links |
| `/api/backup/data\|files` | GET export / POST restore |
| `GET /api/p/{table}/{uuid}` | template/pages |

More detail: [Metadata](docs/metadata.md), [Field Definitions](docs/field-definitions.md), [REST Query Params](docs/rest-query-params.md).

## Testing

```bash
./mvnw -pl snello-control-app -am test
./mvnw -pl snello-control-app -Dtest=io.snello.test.metadata.ProgramsMetadataCreationTest test
```

`compose-test.yml` maps Postgres to host port **5433** so tests can run while the full stack occupies `5432`.

## AI Agent

See [AI Agent](docs/ai-agent.md).
