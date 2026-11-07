# SelectQuery

A SelectQuery defines a virtual REST table backed by custom SQL.

Instead of querying a physical table directly, clients can call standard API endpoints and use a query name as if it were a table name.

In code, the model is defined in [snello-core/src/main/java/io/snello/model/SelectQuery.java](../snello-core/src/main/java/io/snello/model/SelectQuery.java).

## What It Is Used For

Use SelectQuery when you need to:

- expose joined or computed data through REST without creating a physical table
- keep a stable REST endpoint while changing SQL implementation details
- reuse the generic list and count flow from the API layer

At runtime, the API service checks whether the requested table name matches a SelectQuery name. If it does, list and count are executed using the SQL stored in SelectQuery.

## How It Works as a Virtual REST Table

If `query_name` is `user_actions_feed`, these calls work like a normal table endpoint:

- `GET /api/user_actions_feed`
- `GET /api/user_actions_feed?_limit=20&_start=0&_sort=creation_date desc`

The name in the URL is resolved as follows:

1. API receives `/api/{table}` in [snello-core/src/main/java/io/snello/service/rs/ApiServiceRs.java](../snello-core/src/main/java/io/snello/service/rs/ApiServiceRs.java)
2. `table` is passed to `ApiService.list` and `ApiService.count`
3. if `table` exists in `selectqueryMap`, SQL from SelectQuery is executed

## SelectQuery Fields

- `uuid`: unique identifier
- `query_name`: virtual table name used in REST path
- `select_query`: SQL used for list operations
- `select_query_count`: SQL used for count headers and pagination totals
- `with_params`: enables query-parameter binding

## Creating a SelectQuery

SelectQuery entries are managed through the dedicated REST resource:

- `GET /api/selectqueries`
- `GET /api/selectqueries/{uuid}`
- `POST /api/selectqueries`
- `PUT /api/selectqueries/{uuid}`
- `DELETE /api/selectqueries/{uuid}`

Implementation is in [snello-core/src/main/java/io/snello/service/rs/SelectQueryServiceRs.java](../snello-core/src/main/java/io/snello/service/rs/SelectQueryServiceRs.java).

### Example: create a virtual endpoint

```json
{
  "query_name": "user_actions_feed",
  "with_params": true,
  "select_query": "SELECT uuid, action_username, action_name, data_table_name, data_table_key, creation_date FROM user_actions",
  "select_query_count": "SELECT COUNT(*) AS size_of FROM user_actions"
}
```

After creating this entry, you can call:

- `GET /api/user_actions_feed`

## Parameterized Queries (`with_params=true`)

When `with_params=true`, query parameters from the request are forwarded to SQL execution.

Typical usage:

- `GET /api/user_actions_feed?action_username=snello@snello.io&_limit=10`

When `with_params=false`, the query runs without request parameter binding.

## Notes and Constraints

- `query_name` must not be empty and must not use reserved names.
- SelectQuery and Metadata names share the same namespace at API path level. The service prevents collisions.
- Owner protection behavior from metadata (`api_protected`, `username_field`) is metadata-driven. A SelectQuery is executed through its SQL definition, so ownership constraints should be handled in SQL design or through endpoint authorization policy.
