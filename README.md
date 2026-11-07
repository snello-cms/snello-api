# Snello API

![Snello Logo](docs/images/snello-logo.svg)

[![Built with Maven](https://img.shields.io/badge/Built%20with-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.40.1-4695EB?logo=quarkus&logoColor=white)](https://quarkus.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

Snello is a metadata-driven headless CMS API.

It lets you model entities, fields, and query behavior through metadata, then expose REST endpoints without writing one controller per business table.

## Why Snello

- Metadata-first API design
- Business-data backup ZIP: `GET/POST /api/backup/data` (all tables) and `GET /api/backup/data/{metadata_name}` (single table); import skips existing keys and does not run DDL
- Slug primary keys: CREATE TABLE sizes the key VARCHAR to the slug source field when that field is wider than 50
- Dynamic table and field management
- Generic REST layer for CRUD and query operations
- Pluggable document storage (filesystem, S3-compatible)
- Built-in support for derived media generation (formats and webp)


## Release 5.1.1

Patch release on Quarkus **3.40.1.1** (same platform as 5.1.0).

### Business-data backup ZIP

Chunked export/import of **row data** only (not metadata schema, not DDL).

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/backup/data` | ZIP of all non-reserved business tables |
| `GET` | `/api/backup/data/{metadata_name}` | ZIP for a single table |
| `POST` | `/api/backup/data` | multipart field `file` — import ZIP |
| `GET`/`POST` | `/api/backup/files` | not implemented |

ZIP layout:

- `manifest.json` — `kind=snello-backup-data`, `page_size`, per-table row/chunk counts
- `data/{table}/0001.json`, `0002.json`, … — JSON arrays of rows (default page size 500; `snello.backup.data.page-size`)

Import rules: insert with `create` when the table key is missing; **skip** existing keys; **no** `CREATE TABLE` (tables must already exist). Use `POST /api/metadatas/export|import` for schema/fields.

### Slug primary-key VARCHAR sizing

When `table_key_type=slug`, `CREATE TABLE` sizes the key column to the **slug source field** (`table_key_addition`) if that field is wider than the default `VARCHAR(50)`:

| Source field (typical) | Key VARCHAR |
|---|---|
| `input` / `text` / `email` / `password` | 200 |
| `select` / `join` / `media` / … | 2048 |
| `textarea` / `tinymce` / … | 2048 (capped) |
| custom `sql_definition` with `VARCHAR(n)` | `n` |

Applies to PostgreSQL, H2, and MySQL (`SlugKeySizes` + dialect `createTableSql`). Plain `uuid` keys stay at `VARCHAR(50)`.

### Docker tags

- `snellocms/snello-api:5.1.1` / `:latest`
- `ghcr.io/snello-cms/snello-api:5.1.1` / `:latest`

## Documentation for Final Users

Start here:

- [Documentation Index](docs/index.md)

Feature pages:

- [Metadata](docs/metadata.md)
- [Field Definitions](docs/field-definitions.md)
- [SelectQuery](docs/select-query.md)
- [Document Management](docs/document-management.md)
- [REST Query Params](docs/rest-query-params.md)
- [AI Agent](docs/ai-agent.md)
- [AI Tools Schema](docs/ai-tools-schema.md)

## Quick Start

Run the API in development mode:

```bash
./mvnw quarkus:dev
```

Stop local dev dependencies:

```bash
docker compose -f compose-devservices.yml down
```

## Docker Image Reference

Published images (JVM workflow):

- `snellocms/snello-api:5.1.1` (also `:latest`)
- `ghcr.io/snello-cms/snello-api:5.1.1` (also `:latest`)

Example commands:

```bash
docker pull snellocms/snello-api:5.1.1

# Build locally from this repository
docker build -f src/main/docker/Dockerfile.jvm -t snellocms/snello-api:local .

docker run --rm -p 8080:8080 snellocms/snello-api:local
```

## Local Development Stack

In development mode, Quarkus Compose Dev Services starts local dependencies automatically.

Services used in DEV:

- PostgreSQL
- MinIO
- Keycloak

Main compose file:

- compose-devservices.yml

Keycloak realm imports:

- dev/keycloak/realm-snello.json
- dev/keycloak/realm-lovers.json

OIDC realms configured in dev:

- snello realm -> client snello-api
- lovers realm -> client accounting

## Testing

Run all tests:

```bash
./mvnw test
```

Run one test class:

```bash
./mvnw -Dtest=io.snello.test.metadata.ProgramsMetadataCreationTest test
./mvnw -Dtest=io.snello.test.backup.BackupDataZipTest,io.snello.test.metadata.SlugKeySizesTest test
```

## AI Agent

See dedicated page: [AI Agent documentation](docs/ai-agent.md).

## Notes

- TODO and bug backlog is tracked in TODO.md.
