# Actions

An Action is a JavaScript snippet (Nashorn) that runs when a row of a metadata table is created, updated or deleted
through the API.

| Field | Meaning |
|---|---|
| `metadata_name` | table the action belongs to |
| `condition` | `PERSIST` (create), `MERGE` (update), `DELETE` |
| `phase` | `PRE`: runs before the write, synchronously; `POST` (default): runs after it, asynchronously |
| `body` | the script |

Only one phase runs per table + condition: if both a PRE and a POST action exist, PRE wins.

## Script bindings

| Name | Value |
|---|---|
| `map` | row payload (create / update). In PRE it is the map that is saved next: changes made by the script are persisted. `null` on DELETE. |
| `key` | key value of the row (e.g. its uuid or slug) |
| `table` | table name |
| `table_key` | key **column** name (e.g. `uuid`) |
| `action` | the Action itself |
| `db` | `JdbcRepository` (e.g. `db.query("UPDATE t SET x = ? WHERE uuid = ?", java.util.List.of(v, key))`) |
| `metadata`, `documents`, `mail` | metadata service, storage service, mail service |

## Errors

- PRE: an exception (e.g. `throw 'name not allowed'`) aborts the write; the API answers `400` with the message.
- POST: the write is already done; failures are only logged (`ACTION [...] post async execution failed`).

## Security

Scripts run inside the API with full Java access. Creating or changing Actions requires **Admin** or **Manager**.

Example — PRE PERSIST, default a field:

```js
if (map.get('notes') == null) map.put('notes', 'created via API');
```
