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
- `api_protected`: enables row-level security (see below)
- `username_field`: column of the query result that holds the owner username

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

## Filters, sort, paging and total

- Filters (`with_params=true` only), `_sort` and paging are appended to `select_query`. When the query already has
  a top-level `GROUP BY`, `HAVING`, `ORDER BY`, `LIMIT`, `UNION`, ... it is wrapped as
  `SELECT * FROM (<select_query>) snello_q` first, so the filters apply to the query result (filter on its output
  columns, e.g. `?hull=kayak` on `SELECT hull, COUNT(*) ... GROUP BY hull`).
- Queries without `with_params` are now paged and sortable too (they used to return every row).
- `x-total-count`: `select_query_count` when no filter applies, otherwise `COUNT(*)` over `select_query` with the
  same filters (works on every database; it used to be 0 on PostgreSQL / H2).
- Creating or changing a SelectQuery requires Admin or Manager.

## Parameterized Queries (`with_params=true`)

When `with_params=true`, query parameters from the request are forwarded to SQL execution.

Typical usage:

- `GET /api/user_actions_feed?action_username=snello@snello.io&_limit=10`

When `with_params=false`, the query runs without request parameter binding.

## Notes and Constraints

- `query_name` must not be empty and must not use reserved names.
- SelectQuery and Metadata names share the same namespace at API path level. The service prevents collisions.
- `select_query_count` should always be set: without it the count falls back to a table named like the query.

## Row-Level Security (`api_protected` + `username_field`)

A SelectQuery follows the same contract as a protected metadata table:

| caller | result |
|---|---|
| anonymous | `401` |
| `Admin` / `Manager` | every row of the query |
| any other authenticated user | only rows where `username_field` equals the JWT username |

How it is applied:

- the stored SQL is wrapped as `SELECT * FROM (<select_query>) snello_owner_scope` and the owner condition
  `<username_field> = ?` is added to the outer query, so it holds whatever the query's own `WHERE`, `JOIN` or
  `GROUP BY` look like. The username is always a bound parameter, never concatenated.
- `username_field` must be a column (or alias) **exposed by the query**, e.g. `SELECT username AS owner, ... ` with
  `username_field = "owner"`. If it is missing the call fails with a SQL error.
- a `?<username_field>=other` request parameter cannot override the owner: the server value replaces it.
- with `with_params=false` the owner filter is still applied, and other request parameters stay ignored.
- the total count (`x-total-count`) is computed over the same owner-scoped wrap; `select_query_count` is not used
  for protected queries, since nothing guarantees it is owner-filtered.
- `api_protected=true` without `username_field` is rejected (`400`) on create/update; `username_field` must be a plain
  column name and must not end with a filter operator suffix (`_ne`, `_in`, `_like`, ...), which would turn the
  owner equality into another operator.
- an authenticated caller without a username gets `403`.

```json
{
  "query_name": "my_lovers",
  "with_params": true,
  "api_protected": true,
  "username_field": "username",
  "select_query": "SELECT uuid, name, city, username FROM lovers",
  "select_query_count": "SELECT COUNT(*) AS size_of FROM lovers"
}
```

Request values that become SQL text are restricted on every `/api` read (protected or not), because a subquery
there could read rows the owner filter hides:

- `_sort`: `random`, or a comma-separated list of `column`, `column asc|desc`, `column:asc|desc`
- `select_fields`: a comma-separated list of `*`, `t.*`, `column` or `column AS alias`

Request parameter names are validated as plain identifiers (`[A-Za-z_][A-Za-z0-9_.]*`, optionally followed by an
operator suffix such as `_ne`); anything else returns `400`.

## Disabled users

A JWT has no "enabled" claim, so a user disabled or deleted in Keycloak would keep access until the token expires.
`UserEnabledAugmentor` closes that gap: for every authenticated request whose token comes from the realm configured in
`snello.keycloak.admin.target-realm`, the API asks Keycloak (through the admin client) whether the user (`sub`) is
still enabled and answers **401** if not. Anonymous requests and tokens of other realms are not checked.

| Property | Default | Meaning |
|---|---|---|
| `snello.security.check-user-enabled` | `true` | Turn the check off (e.g. no Keycloak admin client available). |
| `snello.security.user-enabled-cache-seconds` | `60` | How long an answer is cached, i.e. the maximum delay before a disabled user is locked out. |

If Keycloak cannot be reached the check fails open (the request goes through, with a warning in the log).
