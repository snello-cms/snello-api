# Snello API Docs

This folder contains the functional and technical documentation for the project.


## Release 5.1.1 highlights

- Business-data backup ZIP: `GET/POST /api/backup/data`, `GET /api/backup/data/{metadata_name}` — see [README — Release 5.1.1](../README.md#release-511)
- Slug keys: CREATE TABLE widens the key VARCHAR to the slug source field when needed

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

## Useful Endpoints

- `GET /api/metadatas`
- `GET /api/fielddefinitions`
- `GET /api/datalist/metadata/{name}`
- `GET /api/datalist/metadata/{name}/fielddefinitions`
