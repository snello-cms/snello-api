# Snello API Docs

Functional and technical documentation for Snello.

Architecture / 6.x proposal (repo root):

- [ARCHITECTURE_REENGINEERING.md](../ARCHITECTURE_REENGINEERING.md)
- [ARCHITECTURE_REENGINEERING.it.md](../ARCHITECTURE_REENGINEERING.it.md)

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
- `GET /api/datalist/metadata/{name}` (facet: identity/query/ui/ai)
- `GET /api/datalist/metadata/{name}/fielddefinitions` (FieldModel facets)
- `GET /api/datalist/metadata/{name}/fields/ui`
- `GET /api/datalist/metadata/{name}/fields/query`
