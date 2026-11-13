# Roadmap: introspect, AI admin pages, admin UI refactor

Working plan agreed on 2026-11-13. Order: **Task 1 → open question → Task 2 → formly spike → Phase 3**.

## Task 1 — Test and improve schema introspect

Code already exists: `IntrospectService`, `SchemaIntrospection`, `IntrospectServiceRs`, see [Schema introspect](introspect.md).

1. **Disposable DB**: dump the real DB and restore it locally. Never wipe metadata on a shared DB.
2. **Save the truth**: export current `metadatas` + `fielddefinitions`.
3. **Wipe metadata**, run `POST /api/introspect/preview` with no table filter.
4. **Diff generated vs truth**, table by table: PK type, field types, join targets, multijoins, textarea/tinymce, missed tables. The diff is the fix list.
5. **Improve heuristics** where it misses. Things the schema does not say, but can be guessed:
   - **Default ORDER BY**: `position` / `sort` / `ordine` column → else `updated_at` / `created_at` desc → else PK.
   - **List fields**: label-like columns (`name`, `title`, `nome`, `descrizione`, `code`) + short, mostly non-null columns; skip text/HTML and audit columns.
   - **Join display label**: same "most label-like column" rule.
   - Mark these as *suggested* in the preview so they get reviewed in the SPA.
6. **Tests**: add a case to `IntrospectServiceTest` for every fix.
7. **Apply** and check the result in snello-admin: list, edit, joins.

**Done when**: regenerated metadata needs only small manual fixes, and the always-manual fields are documented in [introspect.md](introspect.md).

## Open question (blocks Task 2)

flower-ng's AI pages call its own backend: `/api/v1/projects`, `/skills`, `/mcptools`, `/cards`, `/card-catalog`, `/service-accounts`, `/history`, `/me/projects`. **snello-api has none of these.** Decide:

- **(a)** snello-admin calls the AI backend directly: second base URL in config; same token or separate auth?
- **(b)** snello-api proxies those endpoints.

Affects auth, HTTP interceptors and config in snello-admin.

## Task 2 — AI admin pages in snello-admin (port from flower-ng)

Source: `kayak-love/flower-ng` → `src/app/{projects,skills,mcptools,service-accounts,cards,history}`.

1. **Remove the old AI menus**: `pages/aitools/`, `pages/chat-interactions/`, `models/chat-interaction.ts`, their routes, sidebar entries and `admin-home` tiles.
2. **Add CRUD pages**: Projects, Skills, MCP tools, Service accounts, Cards. Plus **Chat history** (read-only list + transcript).
3. **Project scoping**: as in flower-ng, everything belongs to a project (`/me/projects`, project picker in the top bar); every page filters by the active project.
4. **Test**: CRUD on each page against the real API, then one end-to-end run: project → MCP tool → skill using it → card rendering the result → chat → history shows it.

Concept: a **project** groups everything an AI use case needs — skills that use MCP tools (defined via REST APIs), a service account (e.g. to POST data from outside), and cards to render the AI result.

**Build in the new style from the start** (signals, feature folders, flower-ng file naming), so Phase 3 does not rewrite these pages.

**Secrets**: Gemini keys (quick / pro) go in env / k8s secret only — never in code, commits or chat.

## Phase 3 — Admin UI refactor (design first)

### Metadata-driven data pages with formly

[formly](https://formly.dev/) fits because metadata already describes every field.

- Core piece: one **`metadata → FormlyFieldConfig[]` mapper** (semantic_type → field type, join → async select, multijoin → multiselect, tinymce → custom type). Edit/view pages then need no hand-written forms.
- formly is **forms only**: list pages need a metadata-driven table (PrimeNG Table) that uses default order + list fields from Task 1.
- **Spike first (≈1 day)**: confirm an `@ngx-formly/core` release supports Angular 22 and `@ngx-formly/primeng` matches PrimeNG 21.

### AI chatbot everywhere, with contextual skills

Each admin page declares its context (metadata, record, page type); the chat loads the skills for that context.

- **SelectQuery**: skill + MCP tool that lists metadata / tables; SQL is written by chatting. The MCP tool is essentially `SchemaIntrospection` exposed as a tool (see the `snello-mcp` plane in [index](index.md#planes-6x)), so Task 1 feeds this directly.
- **Actions, Scheduled actions, Print templates**: one skill each, knowing internal functions and JS / Qute / JXLS syntax. **Generate the function reference from code** (script bindings) instead of hand-writing it, so it stays current. See [Actions](actions.md), [Print templates](print-templates.md).
- Metadata / actions admin pages improve mostly through these skills.

## Later

- Close the rentals job, then a proof of concept: a rental site at the level of the big rental companies, built on Snello, client-ready to connect external services (REST / WS / DB tables), AI-ready. Own brand and content — match the quality and features, not their sites.
- Integrations come after; Snello is the core.
