# Snello API

![Snello Logo](docs/images/snello-logo.svg)

[![Built with Maven](https://img.shields.io/badge/Built%20with-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.40.1-4695EB?logo=quarkus&logoColor=white)](https://quarkus.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

Snello is a metadata-driven headless CMS API.

It lets you model entities, fields, and query behavior through metadata, then expose REST endpoints without writing one controller per business table.

## Why Snello

- Metadata-first API design
- Dynamic table and field management
- Generic REST layer for CRUD and query operations
- Pluggable document storage (filesystem, S3-compatible)
- Built-in support for derived media generation (formats and webp)

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

Suggested image reference for this project:

- `ghcr.io/snello-cms/snello-api-quarkus:latest`

Example commands:

```bash
# Pull (if published)
docker pull ghcr.io/snello-cms/snello-api-quarkus:latest

# Build locally from this repository
docker build -f src/main/docker/Dockerfile.jvm -t snello-api-quarkus:local .

# Run local image
docker run --rm -p 8080:8080 snello-api-quarkus:local
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
```

## AI Agent

See dedicated page: [AI Agent documentation](docs/ai-agent.md).

## Notes

- TODO and bug backlog is tracked in TODO.md.
