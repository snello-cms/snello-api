# Snello API Docs

Functional and technical documentation for Snello **6.x** (`6.0.12-alpha`).

Architecture (repo root):

- [ARCHITECTURE_REENGINEERING.md](../ARCHITECTURE_REENGINEERING.md)
- [ARCHITECTURE_REENGINEERING.it.md](../ARCHITECTURE_REENGINEERING.it.md)
- [README — Planes & facets](../README.md#planes--facets-6x)

## Index

### Runtimes & ops

- [All-in-one](all-in-one.md) — Docker / Compose (`docker-compose-aio.yaml`, `docker-compose-aio-sso.yaml`)
- [OpenAPI](openapi.md) — `GET /openapi` JSON
- [Backup (data ZIP)](backup.md) — `GET/POST /api/backup/data`

### Modeling

- [Metadata](metadata.md)
- [Metadata: Ownership Protection](metadata.md#ownership-protection-api_protected-and-username_field)
- [Metadata: Table key types](metadata.md#table-key-types)
- [Field Definitions](field-definitions.md)
- [Entity drafts](drafts.md) — `/api/drafts/validate|apply`
- [SelectQuery](select-query.md)
- [REST Query Params](rest-query-params.md)

### Documents & AI

- [Document Management](document-management.md)
- [AI Agent](ai-agent.md)
- [AI Tools Schema](ai-tools-schema.md)

## Where to Start

1. Pick a runtime: full stack (`compose.yml`), or [All-in-one](all-in-one.md).
2. Model entities with [Metadata](metadata.md) + [Field Definitions](field-definitions.md), or [Drafts](drafts.md).
3. For SPA reads, prefer **datalist facet** endpoints over flat `/api/fielddefinitions`.
4. Discover the live API surface: [OpenAPI](openapi.md).

## Planes (6.x)

| Plane | Image | Surface |
|---|---|---|
| **Control** | `snello-control` (`:8080`) | Full API: data CRUD, metadata/DDL, documents, auth, AI, drafts, backup |
| **Query** | `snello-query` (`:8081`) | Read-only GETs on declared metadata / select queries |
| **All-in-one** | `snello-all-in-one` (`:8080`) | Full control API + **H2** + filesystem docs + **snello-admin** SPA; OIDC |
| **MCP** | `snello-mcp` | AI / tools shell (protocol TBD) |

Same flat DB tables (`metadatas` / `fielddefinitions`); concerns split in Java facets (`identity` / `query` / `ui` / `ai`) and in REST contracts.

## Useful Endpoints

OpenAPI (any plane — public):

- `GET /openapi` — OpenAPI 3 **JSON**
- `GET /q/openapi` — YAML (or JSON via `Accept` / `?format=json`)

Control / all-in-one — admin + facets:

- `GET /api/metadatas` / `GET /api/fielddefinitions` (flat CRUD rows)
- `GET /api/datalist/metadata/{name}` (legacy flat `Metadata` for SPA)
- `GET /api/datalist/metadata/{name}/fielddefinitions` (legacy flat `FieldDefinition[]`)
- `GET /api/datalist/metadata/{name}/model` (6.x facets: identity/query/ui/ai)
- `GET /api/datalist/metadata/{name}/fieldmodels` (6.x `FieldModel` facets)
- `GET /api/datalist/metadata/{name}/fields/ui|query|ai`
- `GET /api/datalist/metadata/{name}/ui` (`EntityUiView`)
- `GET /api/datalist/metadata/{name}/ai` (`EntityAiView`)
- `POST /api/drafts/validate` / `POST /api/drafts/apply`
- `GET/POST /api/backup/data` — see [Backup](backup.md)

Query plane (`:8081`) — read-only data GETs only:

- `GET /api/{table}` / `GET /api/{table}/{uuid}` (declared metadata / select queries)

## All-in-one dump round-trip

See [All-in-one](all-in-one.md#seed-metadata--data-optional) and [README — dump IT](../README.md#all-in-one-dump--export--import-it):

1. `./dev/scripts/generate-metadatas-export.sh`
2. Import metadatas → create tables → `POST /api/backup/data`
3. Or `./dev/scripts/aio-import-from-exports.sh` / `./dev/scripts/run-aio-import-tests.sh`
