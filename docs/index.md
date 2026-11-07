# Snello API Docs

Functional and technical documentation for Snello.

Architecture / 6.x (repo root; facets + control/query planes largely landed):

- [ARCHITECTURE_REENGINEERING.md](../ARCHITECTURE_REENGINEERING.md)
- [ARCHITECTURE_REENGINEERING.it.md](../ARCHITECTURE_REENGINEERING.it.md)
- [README — Planes & facets](../README.md#planes--facets-6x)

## Index

- [Metadata](metadata.md)
- [Metadata: Ownership Protection](metadata.md#ownership-protection-api_protected-and-username_field)
- [Field Definitions](field-definitions.md)
- [SelectQuery](select-query.md)
- [Document Management](document-management.md)
- [REST Query Params](rest-query-params.md)
- [AI Agent](ai-agent.md)
- [AI Tools Schema](ai-tools-schema.md)

## Where to Start

If you are modeling a new application entity:

1. read [Metadata](metadata.md) first
2. then complete the configuration with [Field Definitions](field-definitions.md)
3. for SPA reads, prefer datalist facet endpoints (below) over flat `/api/fielddefinitions`

## Useful Endpoints

Control plane (`:8080`) — admin + facets:

- `GET /api/metadatas` / `GET /api/fielddefinitions` (flat CRUD rows)
- `GET /api/datalist/metadata/{name}` (facet: identity/query/ui/ai)
- `GET /api/datalist/metadata/{name}/fielddefinitions` (FieldModel facets)
- `GET /api/datalist/metadata/{name}/fields/ui|query|ai`
- `GET /api/datalist/metadata/{name}/ui` (`EntityUiView`)
- `GET /api/datalist/metadata/{name}/ai` (`EntityAiView`)

Query plane (`:8081`) — read-only data GETs only:

- `GET /api/{table}` / `GET /api/{table}/{uuid}` (declared metadata / select queries)

## All-in-one dump round-trip

See [README — All-in-one dump → export → import IT](../README.md#all-in-one-dump--export--import-it):

1. `./dev/scripts/generate-metadatas-export.sh` (dump → `metadatas-from-dump.json`)
2. `./dev/scripts/run-aio-import-tests.sh` (import into Docker all-in-one + Failsafe IT)
