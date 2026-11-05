# Reingegnerizzazione Snello: facet + query plane

> **Stato:** proposta per discussione di team — non ancora un impegno di implementazione.
>
> Versione inglese: [ARCHITECTURE_REENGINEERING.md](ARCHITECTURE_REENGINEERING.md)

## Diagnosi (stato attuale)

Oggi [`Metadata`](src/main/java/io/snello/model/Metadata.java) e [`FieldDefinition`](src/main/java/io/snello/model/FieldDefinition.java) sono bag di proprietà pubbliche mappate 1:1 su `metadatas` / `fielddefinitions`. Il runtime le consuma in modo misto:

- **Query/DDL**: `table_name`, `table_key*`, `select_fields`, `order_by`, `alias_*`, `creation_query`, `type`/`input_type`/`sql_definition`/`join_*` via [`ApiService`](src/main/java/io/snello/service/ApiService.java) + `*JdbcRepository`
- **UI (SPA)**: `label`, `options`, `group_name`, `tab_*`, `icon`, `show_in_list`, … serviti da [`DataListServiceRs`](src/main/java/io/snello/service/rs/DataListServiceRs.java), quasi mai usati server-side
- **AI**: solo `description` + remap tipi in [`FieldTypeUtils`](src/main/java/io/snello/util/FieldTypeUtils.java) / [`SnelloCmsTools`](src/main/java/io/snello/service/ai/tools/) — non c’è ancora MCP

Il monolitico Quarkus ha un solo deployable; la “cache” metadati è una `Map` in [`MetadataService`](src/main/java/io/snello/service/MetadataService.java) invalidata da eventi CDI.

## Principio guida

**Stesso storage, concern separati in Java (e nei contratti).** Le tabelle restano piatte; i model diventano composizione di facet. Ogni runtime carica solo le facet che gli servono.

```mermaid
flowchart TB
  subgraph storage [Storage invariato]
    TMeta[metadatas]
    TField[fielddefinitions]
  end

  subgraph domain [Domain model]
    Entity[EntityModel]
    Q[QueryFacet]
    A[AiFacet]
    U[UiFacet]
    Entity --> Q
    Entity --> A
    Entity --> U
  end

  TMeta --> Entity
  TField --> Entity

  subgraph runtimes [Deployables]
    Control[snello-control]
    Query[snello-query]
    Mcp[snello-mcp]
    AllInOne[snello-all-in-one]
  end

  Entity --> Control
  Q --> Query
  A --> Mcp
  U --> Control
  Entity --> AllInOne
```

## 1. Spezzatura model (fase fondante)

Introdurre package `io.snello.model.entity` (o `facet`) senza rompere subito i consumer esistenti.

### Entity (identità)

- `uuid`, `table_name` (nome logico / API resource)
- `metadata_group` (tassonomia)
- lifecycle: `already_exist`, `created`

### QueryFacet (Metadata)

| Campo | Ruolo |
|---|---|
| `table_key`, `table_key_type`, `table_key_addition` | PK / generation |
| `select_fields`, `order_by` | list default |
| `alias_table`, `alias_condition` | physical redirect |
| `creation_query` | DDL override |
| `api_protected`, `username_field` | row-level filter (query-time) |

### UiFacet (Metadata)

| Campo | Ruolo |
|---|---|
| `icon`, `tab_groups` | shell UI |
| `calendar_*` | vista calendario |
| `search_fields` | hint UI (oggi inutilizzato server-side) |

### AiFacet (Metadata) — nuovo concern esplicito

Oggi quasi assente; va progettato per authoring AI:

- `description` (già c’è) → elevare a testo obbligatorio per tools
- `ai_summary` / `ai_synonyms` (nuove colonne *solo se necessarie*; altrimenti JSON in colonna `ai_meta` — preferenza: **una colonna JSON `ai_profile`** per non proliferare ALTER)
- `tool_visibility` (esporre in MCP/`listEntities` o no)
- `examples` (payload esempio per create/update)

### FieldDefinition → tre facet

```mermaid
classDiagram
  class FieldModel {
    uuid
    metadata_uuid
    name
  }
  class FieldQueryFacet {
    sql_type
    sql_definition
    default_value
    table_key
    join_table_name
    join_table_key
    searchable
    search_condition
    search_field_name
  }
  class FieldUiFacet {
    label
    type
    input_type
    options
    group_name
    tab_name
    validations
    view_index
    show_in_list
    input_disabled
    function_def
    pattern
    mandatory
    required
    order_num
  }
  class FieldAiFacet {
    description
    ai_hint
    semantic_type
    examples
  }
  FieldModel --> FieldQueryFacet
  FieldModel --> FieldUiFacet
  FieldModel --> FieldAiFacet
```

**Regola di mapping:** `fromMap`/`toMap` restano sul root `FieldModel` / `EntityModel` (compatibilità tabelle), ma i servizi tipizzano contro le facet.

**Pulizia immediata inclusa:** bug in `FieldDefinition.fromMap` (`validations` → `metadata_name`; instanceof sbagliati su `view_index`/`mandatory`/`order_num`); `search_fields` mancante in `Metadata.toMap()`.

**Enum tipizzati** (sostituiscono stringhe dove possibile, serializzati come stringa DB): `WidgetType`, `InputType`, `KeyType`, `SearchOp` — senza migration se i valori restano gli stessi letterali.

Backward compat: tenere `Metadata` / `FieldDefinition` come **facade deprecata** che delega alle facet per 1–2 cicli, così `ApiService` e RS non si riscrivono in un colpo solo.

## 2. Contratti AI-first (authoring metadati)

Assunzione: **l’AI diventa il writer principale** di metadati; l’UI admin diventa editor assistito.

- Definire JSON Schema (o record Java + OpenAPI) per:
  - `EntityDraft` = QueryFacet + AiFacet (+ UiFacet opzionale)
  - `FieldDraft` = name + QueryFacet minimo + AiFacet + UiFacet default derivabile
- Tools CMS esistenti (`SnelloCmsTools`) accettano/emettono **draft tipizzati**, non `Map` grezze
- Derivazioni automatiche (riducono carico sull’AI):
  - da `semantic_type` / `sql_type` → default `type`/`input_type` UI
  - da `show_in_list`/`searchable` → ricomposizione `select_fields` / search hints
  - validazione: nome colonna, reserved words ([`MetadataUtils`](src/main/java/io/snello/util/MetadataUtils.java)), join coerenti
- Layer MCP (nuovo modulo/runtime): tools read (`list_entities`, `describe_entity`, `query_rows`) e write (`propose_entity`, `apply_entity`) con dry-run DDL

Output target per AI: profilo “query + AI” obbligatorio; UI auto-suggerita e override umano/AI opzionale.

## 3. Separazione runtime: control vs query

**Default scelto:** un solo Maven project (oggi un solo [`pom.xml`](pom.xml)), **Quarkus build profiles / packaging** che producono più immagini Docker — non un multi-module Maven subito (costo alto, stesso codice condiviso).

| Immagine | Capacità | Dipendenze facet |
|---|---|---|
| `snello-control` | CRUD metadati, DDL, admin RS, AI write tools | Query + UI + AI |
| `snello-query` | solo `GET/POST list/fetch` su `/api/{table}` (+ count); **no** create metadata, **no** DDL, **no** truncate | solo QueryFacet (+ security row filter) |
| `snello-mcp` | MCP server + schema tools; può puntare a query plane | AiFacet + QueryFacet |
| `snello-all-in-one` | API full control + query + SPA embedded; **H2 in-memory**; singolo container per demo / local / eval | Query + UI + AI |

Meccanismo tecnico Quarkus:

- `@IfBuildProfile("query")` / `@UnlessBuildProfile("query")` su RS e CDI beans
- oppure exclusion di package in `quarkus.arc.exclude-types` / index filtering per profilo
- Dockerfile dedicati sotto `src/main/docker/` (es. `Dockerfile.query.jvm`, `Dockerfile.all-in-one.jvm`)

### Immagine all-in-one (H2 + Angular SPA)

Obiettivo: un container che avvia Snello usabile senza DB esterno e con UI inclusa — per demo, onboarding, smoke CI ed esperimenti di authoring AI.

```mermaid
flowchart LR
  subgraph aio [snello-all-in-one container]
    SPA[Angular static assets]
    API[Quarkus full API]
    H2[(H2 in-memory)]
    SPA -->|same origin /api| API
    API --> H2
  end
  AngularRepo[snello-angular sibling repo] -->|CI build copy into image| SPA
```

Scelte fissate per questa immagine:

- **DB:** H2 in-memory (`snello.dbtype=h2`), schema bootstrap all’avvio dai path init/DDL esistenti; dati effimeri salvo eventuale volume H2 file-based in seguito
- **Frontend:** la nuova app Angular vive in un **repository separato**; il build Docker all-in-one consuma un `dist/` precompilato (CI multi-repo / multi-stage: clone o download artifact → copia in Quarkus `META-INF/resources` o layer statico servito da Quarkus)
- **Superficie API:** full (control + data CRUD), non query-only — allineata a “prova tutto in un box”
- **Same origin:** SPA e API sullo stesso host/port per evitare CORS nel path demo
- **Non è target HA di produzione:** la produzione resta control + query replicas + MySQL/PostgreSQL esterno; all-in-one è il canale di comodità/distribuzione

### Nodi query + cache + replica

```mermaid
sequenceDiagram
  participant LB as LoadBalancer
  participant Q1 as QueryNode1
  participant Q2 as QueryNode2
  participant MetaCache as LocalMetaCache
  participant DB as DbPrimaryOrReplica

  LB->>Q1: GET /api/orders
  Q1->>MetaCache: EntityQueryView orders
  alt cache miss or stale
    Q1->>DB: load metadatas + fielddefinitions
    Q1->>MetaCache: store versioned snapshot
  end
  Q1->>DB: SELECT from QueryFacet
  Q1-->>LB: rows
```

Design cache (sostituisce le Map package-private attuali):

- Snapshot versionato: `meta_version` (timestamp o hash da riga di controllo / `MAX(updated_at)` se aggiungi colonna, altrimenti checksum su uuid+campi query)
- Ogni nodo query: refresh periodico + invalidazione su webhook/event bus opzionale da control plane
- Nessuna scrittura metadati sul query plane → coerenza semplice
- JDBC: datasource **read-only** verso replica DB quando disponibile; fallback primary in read-only mode a livello app (`SET SESSION TRANSACTION READ ONLY` / flag Quarkus)

Out of scope iniziale: shard per tenant; focus = N repliche stateless dietro LB.

## 4. Refactor servizi (allineato alle facet)

Ordine interno, dopo i model:

1. Estrarre `EntityQueryView` / `FieldQueryView` (proiezioni immutabili) usate da `ApiService.list/fetch`
2. Muovere costruzione SQL verso un unico `SqlQueryBuilder` (oggi triplicato in mysql/postgresql/h2)
3. `MetadataService` → `EntityCatalog` con API: `queryView(name)`, `uiView(name)`, `aiView(name)`
4. RS admin restano su control; `ApiServiceRs` mutazioni dati restano su control (o profilo `full`); query image espone solo list/fetch/count

## 5. Roadmap incrementalmente shippable

1. **Facet + facade** (nessuna nuova colonna; fix fromMap; enum; catalog views) — basso rischio
2. **AiFacet JSON + draft schema + tool rewrite** — sblocca authoring AI
3. **Build profile `query` + Dockerfile + compose multi-replica** — sblocca scaling read
4. **Immagine all-in-one** (H2 in-memory + Angular SPA da sibling repo) — sblocca demo one-command
5. **Immagine MCP** (se serve protocollo esterno oltre LangChain4j attuale)
6. **SqlQueryBuilder unificato** + hardening identifier quoting

## Decisioni fissate in questa proposta

- Tabelle DB **non** spezzate; eventuale solo colonna JSON `ai_profile` (e analoga su field) quando serve
- Un repo Maven (`snello-api`), **profili Quarkus** → immagini Docker multiple
- Query nodes: DB condiviso/replica + cache metadati locale versionata
- UI facet resta first-class per la SPA, ma **derivabile** da AI/query per ridurre lavoro di authoring
- Compatibilità: facade `Metadata`/`FieldDefinition` durante la migrazione dei consumer
- **Immagine Docker all-in-one** con API full + **H2 in-memory** + build statico del **nuovo frontend Angular** (progetto separato); per demo/local/eval, non come topologia HA di produzione
- Angular resta un **repository sibling**; coupling solo a build-time (artifact nell’immagine), non un Maven submodule dentro `snello-api`

## Aperti alla discussione

- Ordine di priorità degli step di roadmap
- Se MCP serve come immagine separata in v1, o bastano i tool LangChain4j all’inizio
- Naming delle immagini Docker / profili Quarkus
- Se all-in-one debba supportare persistenza H2 file-based opzionale via volume
