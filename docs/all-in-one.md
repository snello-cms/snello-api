# All-in-one runtime

`snello-all-in-one` is a **single JVM image** with the full **control-plane** API, embedded **H2**, and **filesystem** document storage.

| Included | Not included |
|---|---|
| Full REST API (CRUD, metadata/DDL, documents, auth admin, AI, drafts, backup, OpenAPI) | Embedded SPA / admin UI |
| H2 database (file under `/data` by default) | PostgreSQL / MySQL |
| Filesystem docs (`SNELLO_FS_BASE_PATH`) | MinIO / S3 (optional; unused when `snello.storagetype=fs`) |
| OIDC (external or bundled Keycloak) | — |

Image: `snellocms/snello-all-in-one:alpha` (or `:local` after a local build).

Build the jar first (needed for `--build` and for `docker build`):

```bash
./mvnw -pl snello-all-in-one-app -am package -DskipTests
```

---

## 1. Run with `docker`

External SSO required. Persist H2 + files with a volume on `/data`.

```bash
docker build -f snello-all-in-one-app/src/main/docker/Dockerfile.jvm \
  -t snellocms/snello-all-in-one:local snello-all-in-one-app

docker run --rm -p 8080:8080 \
  -v snello-aio-data:/data \
  -e QUARKUS_OIDC_AUTH_SERVER_URL=https://your-sso/realms/snello \
  -e QUARKUS_OIDC_CLIENT_ID=snello-api \
  -e QUARKUS_OIDC_APPLICATION_TYPE=service \
  -e QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=resource_access/snello-api/roles \
  snellocms/snello-all-in-one:local
```

What lives under `/data`:

| Path | Content |
|---|---|
| `/data/snello.mv.db` (and related H2 files) | Metadata + business tables |
| `/data/files/` | Uploaded documents |

Ephemeral (no persistence):

```bash
docker run --rm -p 8080:8080 \
  -e SNELLO_JDBC_URL='jdbc:h2:mem:snello;DB_CLOSE_DELAY=-1;MODE=MySQL;IGNORECASE=TRUE;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE' \
  -e QUARKUS_OIDC_AUTH_SERVER_URL=https://your-sso/realms/snello \
  snellocms/snello-all-in-one:local
```

Useful env vars:

| Variable | Default / notes |
|---|---|
| `QUARKUS_OIDC_AUTH_SERVER_URL` | **required** — OIDC realm / issuer URL |
| `QUARKUS_OIDC_CLIENT_ID` | `snello-api` |
| `QUARKUS_OIDC_APPLICATION_TYPE` | `service` (bearer tokens) |
| `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH` | `resource_access/snello-api/roles` |
| `SNELLO_JDBC_URL` | file H2 at `/data/snello`; override with `jdbc:h2:mem:…` for ephemeral |
| `SNELLO_FS_BASE_PATH` | `/data/files` |
| `SNELLO_KEYCLOAK_ADMIN_*` | optional admin bootstrap against the same SSO |
| `OPENAI_API_KEY` | optional; needed only for `/api/chat` |

Check:

```bash
curl -s http://127.0.0.1:8080/api/datalist/names
curl -s http://127.0.0.1:8080/openapi | head -c 200
```

---

## 2. Run with Docker Compose — external SSO

File: [`docker-compose-aio.yaml`](../docker-compose-aio.yaml)

- One service: `snello-all-in-one`
- Named volume `snello-aio-data` → `/data` (H2 file + documents)
- You provide the OIDC issuer URL

```bash
export QUARKUS_OIDC_AUTH_SERVER_URL=https://your-sso/realms/snello
# optional:
# export QUARKUS_OIDC_CLIENT_ID=snello-api
# export OPENAI_API_KEY=...

docker compose -f docker-compose-aio.yaml up -d --build
```

API: http://127.0.0.1:8080

Stop / wipe data:

```bash
docker compose -f docker-compose-aio.yaml down      # keep volume
docker compose -f docker-compose-aio.yaml down -v   # delete H2 + files
```

---

## 3. Run with Docker Compose — bundled Keycloak (H2)

File: [`docker-compose-aio-sso.yaml`](../docker-compose-aio-sso.yaml)

| Service | Image | Persistence | Host port |
|---|---|---|---|
| `keycloak` | `quay.io/keycloak/keycloak:26.8.2` | volume `snello-aio-keycloak-data` (`--db=dev-file`) | **8180** |
| `snello-all-in-one` | `snellocms/snello-all-in-one:alpha` | volume `snello-aio-data` (file H2 + files) | **8080** |

Realm JSON is imported from [`dev/keycloak/`](../dev/keycloak/) (`realm-snello.json`, `realm-lovers.json`) — same dump as the full stack.

OIDC inside the compose network uses `http://keycloak:8080/realms/snello` so the token `iss` matches what Quarkus validates.

```bash
docker compose -f docker-compose-aio-sso.yaml up -d --build
```

| URL | Purpose |
|---|---|
| http://127.0.0.1:8080 | Snello API |
| http://127.0.0.1:8080/openapi | OpenAPI JSON |
| http://127.0.0.1:8180 | Keycloak admin / token endpoint |

Default Keycloak bootstrap admin: `admin` / `admin` (master realm). Dev user in realm `snello` (see realm dump): typically `admin` / `admin` with client `snello-api`.

Mint a token **from the compose network** (issuer host must be `keycloak`, not `localhost`):

```bash
COMPOSE_NET=snello-aio-sso_default
docker run --rm --network "$COMPOSE_NET" curlimages/curl:8.12.1 -sf \
  -X POST 'http://keycloak:8080/realms/snello/protocol/openid-connect/token' \
  -d 'client_id=snello-api' -d 'username=admin' -d 'password=admin' -d 'grant_type=password'
```

Stop / wipe:

```bash
docker compose -f docker-compose-aio-sso.yaml down
docker compose -f docker-compose-aio-sso.yaml down -v   # wipe Snello H2 + Keycloak H2
```

---

## Seed metadata + data (optional)

Committed exports under `dev/exports/`:

- `metadatas-from-pg.json`
- `snello-backup-data.zip`

With the SSO stack up:

```bash
./dev/scripts/aio-import-from-exports.sh
# expects AIO at :18080 by default for compose-allinone-local;
# override for this compose:
export AIO_BASE_URL=http://127.0.0.1:8080
export COMPOSE_NET=snello-aio-sso_default
./dev/scripts/aio-import-from-exports.sh
```

Flow: import metadatas → `GET /api/metadatas/{uuid}/create` per table → `POST /api/backup/data` with the ZIP.

See also [Backup](backup.md) and [README — All-in-one dump IT](../README.md#all-in-one-dump--export--import-it).

---

## Related compose files (legacy / IT)

| File | Role |
|---|---|
| `docker-compose-aio.yaml` | **preferred** — external SSO + H2 volume |
| `docker-compose-aio-sso.yaml` | **preferred** — Keycloak (dev-file H2) + all-in-one |
| `compose-allinone.yml` | older alias of external-SSO layout |
| `compose-allinone-local.yml` | local Keycloak on `:18180` / API on `:18080` (mem H2) |
| `compose-allinone-test.yml` | CI / Failsafe (OIDC off) |
