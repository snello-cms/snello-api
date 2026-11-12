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
| `prints` | renders a Print Template: `prints.print('events-year', {begin_date_gte: '2026-01-01'}, 'begin_date:asc')` returns `bytes()`, `contentType()`, `fileName()`, `rows()` (see [print-templates.md](print-templates.md)) |

## Errors

- PRE: an exception (e.g. `throw 'name not allowed'`) aborts the write; the API answers `400` with the message.
- POST: the write is already done; failures are only logged (`ACTION [...] post async execution failed`).

## Security

Scripts run inside the API with full Java access. Creating or changing Actions requires **Admin** or **Manager**.

Example — PRE PERSIST, default a field:

```js
if (map.get('notes') == null) map.put('notes', 'created via API');
```

## Scheduled Actions

A Scheduled Action is a script run on a schedule instead of on a row write. Rows live in `scheduledactions`
(`/api/scheduledactions`, admin page *Scheduled Actions*).

| Field | Meaning |
|---|---|
| `name`, `description` | label |
| `frequency` | cron: 5 fields (`0 17 * * *` = every day at 17:00) or Quartz with seconds (`0 0 17 * * ?`) |
| `enabled` | `false` keeps the job but never runs it |
| `body` | the script: same bindings as an Action (`db`, `mail`, `http`, `documents`, `metadata`, `log`, `action`); `table`, `table_key`, `key` are `null`, `map` is empty |
| `next_run`, `last_run`, `last_status` (`RUNNING`/`OK`/`ERROR`), `last_message`, `last_duration_ms` | written by the scheduler, ignored on input |

One timer polls the table and runs the jobs whose `next_run` has passed:

```properties
snello.scheduled-actions.enabled=true            # SNELLO_SCHEDULED_ACTIONS_ENABLED
snello.scheduled-actions.cron=0 */5 * * * ?      # tick, SNELLO_SCHEDULED_ACTIONS_CRON (0 * * * * ? = every minute)
snello.scheduled-actions.timezone=Europe/Rome    # zone of the jobs' cron, SNELLO_SCHEDULED_ACTIONS_TIMEZONE (default: server zone)
```

- The tick is the precision: with the default 5 minutes, a job "every minute" runs every 5 minutes.
- Runs missed while the server was down collapse into one run at the next tick. Saving or re-enabling a job
  recomputes `next_run` from now (no replay).
- With several instances on one database, each due run is claimed with a conditional UPDATE, so it runs once.
- Only the control plane (and all-in-one) runs jobs; query nodes never do.
- Scripts cannot look up CDI beans (`Arc.container()`) or MicroProfile Config (`ConfigProvider`) by class name:
  the script class loader resolves other copies of those classes. Use the bindings, and `java.lang.System.getenv()`
  for deployment settings.
- `GET /api/scheduledactions/preview?frequency=0 17 * * *&count=5` lists the next fire times;
  `POST /api/scheduledactions/{uuid}/run` runs a job now (Admin/Manager) and returns its outcome.

Example — every day at 23:30 (`30 23 * * *`), a PDF of this year's events stored as a Document:

```js
var today = java.time.LocalDate.now(), year = today.getYear();
var out = prints.print('events-year', {begin_date_gte: year + '-01-01', begin_date_lt: (year + 1) + '-01-01'}, 'begin_date:asc');
var name = 'events-' + year + '-report-' + today + '.pdf', uuid = java.util.UUID.randomUUID().toString();
var stored = documents.write(out.bytes(), uuid, 'events', 'pdf');
var row = new java.util.HashMap();
row.put('uuid', uuid); row.put('name', name); row.put('original_name', name); row.put('path', stored.get('path'));
row.put('mimetype', out.contentType()); row.put('size', out.bytes().length);
row.put('table_name', 'events'); row.put('table_key', 'report-' + year);
db.create('documents', 'uuid', row);
```
