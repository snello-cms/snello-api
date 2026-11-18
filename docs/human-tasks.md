# Human tasks and workflow pages

A workflow (Quarkus Flow, or anything with an Admin / Manager token) that needs a person's decision opens a **human
task** in snello. The reviewer gets a link to a **workflow page**, an HTML + JS page stored in snello and served at
`/p/{name}?task={uuid}`. The page shows whatever it wants (the record, documents, the workflow's progress...) and ends
with the decision buttons. Snello checks every rule; the page has no power of its own.

## Tasks: `/api/humantasks`

| | |
|---|---|
| `POST /api/humantasks` (Admin / Manager) | `{title, description?, page_name?, workflow_namespace?, workflow_name?, workflow_version?, instance_id?, record_table?, record_key?, decisions? ("APPROVE,REJECT"), roles? ("Admin,Manager"), data?}` → the task + `url` |
| `GET /api/humantasks?status=&instance_id=&record_table=&record_key=&workflow_name=` | tasks the caller may see |
| `GET /api/humantasks/{uuid}` | one task |
| `GET /api/humantasks/{uuid}/context` | `{task, record, fields, recordTitle, workflow, user}`: what pages show |
| `POST /api/humantasks/{uuid}/decide` | `{decision, comment?}` |
| `POST /api/humantasks/{uuid}/undo` | clears the decision |
| `POST /api/humantasks/{uuid}/close` / `cancel` (Admin / Manager) | `{note?}`: the workflow applied it / dropped it |

Life cycle: `OPEN` → (decide / undo, any number of times) → `CLOSED` or `CANCELLED`, then 409 to any change.

Rules, checked by snello:
- **Viewers:** Admin / Manager, or a user with one of the task's `roles`. Anonymous → 401, others → 403. Lists only
  show tasks the caller may see.
- **Decisions:** a decision must be one of the task's `decisions` (400 otherwise).
- **UNDO:** possible while the task is `OPEN`. The workflow applies a decision only if it is still unchanged after its
  grace period, then closes the task. That grace period is the UNDO window.
- **POST permission:** the HTTP policy only allows `POST` on `/api/*` to Admin / Manager / User. A task role outside
  those also needs that policy extended.

`url` = `snello.public.url` (env `SNELLO_PUBLIC_URL`) + `/p/{page_name or "task"}?task={uuid}`.

Tasks are written through `ApiService`, so **Actions** on table `humantasks` run as on any table. For example, a
`PERSIST POST` action can mail the people with the task's roles.

## Workflow pages: `/api/workflowpages` and `/p/{name}`

`workflowpages` rows (admin **Workflow Pages**): `name` (lower case, digits, `-`: the URL), `title`, `description`,
`workflow_namespace`, `workflow_name`, `roles` (informative), `body` (HTML + JS).

`GET /p/{name}?task=...` serves the body in a shell that defines `window.snello`:

```js
await snello.ready();                        // Keycloak login (public client), token refresh
const ctx = await snello.context();          // {task, record, fields, workflow, user}
snello.onChange(ctx => { ... });             // every 10 s and after each decision
await snello.api.get('rent_contracts/' + id) // REST with the viewer's token; post / put / del; blob(path) for files
await snello.decide('APPROVE', 'comment');   // or snello.undo()
snello.renderDecisions(el);                  // status + comment + one button per decision + UNDO
snello.renderTask(el);                       // the generic page: the record's fields + the decision bar
snello.esc(text);                            // HTML escaping for your own markup
snello.params                                // the URL query as an object: /p/new-event?key=abc -> {key: 'abc'}
snello.fatal(err)                            // full-page "not allowed / not found" for err.status 403 / 404
snello.safeHtml(html)                        // rich text from a record -> HTML safe for innerHTML (formatting only)
```

Record values are written by other people: put them in the page with `snello.esc` / `textContent`, and rich text
(tinymce fields) only through `snello.safeHtml`, never straight into `innerHTML`. The `sp-*` classes are styled on
every page.

A page does not need a task: `/p/new-event?key={uuid}` calls `snello.ready()`, reads the record with
`snello.api.get('events/' + snello.params.key)` and never calls `context()` (which needs `?task=`). The viewer still
logs in, and the table's own rules decide what they may read.

`/p/task?task=...` is the built-in generic page, also used for a page with an empty body. `/p/_/snello-page.js` is
the client. The HTML carries no data: personal data comes only from `context` / the API, after login.

Configuration:

| Property (env) | Default | |
|---|---|---|
| `snello.public.url` (`SNELLO_PUBLIC_URL`) | `""` (relative links) | base of task links |
| `snello.pages.keycloak.client-id` | `snello-admin` | public client the pages log in with; its redirect URIs / web origins must allow the snello host |
| `snello.pages.keycloak.url` / `.realm` | from `quarkus.oidc.auth-server-url` | |

In the all-in-one image, `/p/` is excluded from the admin SPA fallback.
