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
./mvnw quarkus:dev
```

Quarkus Compose Dev Services starts dependencies from `compose-devservices.yml` (PostgreSQL, Keycloak, MinIO/Silo).

Stop local dependencies:

```bash
docker compose -f compose-devservices.yml down
```

## Docker images

Published names (no `-quarkus` suffix):

- Docker Hub: `snellocms/snello-api`
- GHCR: `ghcr.io/snello-cms/snello-api`

```bash
# Alpha (6.x)
docker pull snellocms/snello-api:alpha

# Build locally
docker build -f src/main/docker/Dockerfile.jvm -t snellocms/snello-api:local .
docker run --rm -p 8080:8080 snellocms/snello-api:local
```

## Compose files

| File | Purpose |
|---|---|
| `compose-devservices.yml` | Quarkus `%dev` dependencies |
| `compose-test.yml` | Quarkus test dependencies (PostgreSQL) |
| `compose.yml` | Full stack + `snellocms/snello-api:alpha` |

```bash
# Full local stack (API image + deps)
docker compose up -d
```

Pinned service images (current):

- `postgres:18.6`
- `quay.io/keycloak/keycloak:26.8.2`
- `pgsty/silo:RELEASE.2026-09-16T00-00-00Z` (MinIO-compatible; official `minio/minio` is no longer publicly pullable)
- `amazon/aws-cli:2.31.38` (bucket bootstrap)

DB seed / restore scripts live under `dev/db/`. Keycloak realm imports under `dev/keycloak/`.

## Testing

```bash
./mvnw test
./mvnw -Dtest=io.snello.test.metadata.ProgramsMetadataCreationTest test
```

## AI Agent

See [AI Agent documentation](docs/ai-agent.md).
