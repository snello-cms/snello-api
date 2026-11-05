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

DB seed / restore scripts live under `dev/db/`. Keycloak realm imports under `dev/keycloak/`.

## Testing

```bash
./mvnw -pl snello-control-app -am test
./mvnw -pl snello-control-app -Dtest=io.snello.test.metadata.ProgramsMetadataCreationTest test
```

## AI Agent

See [AI Agent](docs/ai-agent.md).
