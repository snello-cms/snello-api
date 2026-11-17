# Databases

Snello control / query / MCP images ship **all JDBC drivers** on the classpath and specialize one dialect at container start (Keycloak-style **mutable-jar re-augmentation**).

Two knobs must stay aligned:

| Knob | Role |
|---|---|
| `SNELLO_DB_KIND` (or `QUARKUS_DATASOURCE_DB_KIND`) | Build-time / re-augment: which Quarkus `quarkus.datasource.db-kind` driver is wired |
| `snello.dbtype` | Runtime: which `JdbcRepository` dialect bean `DbProducer` selects |

`snello-optimize.sh` sets **both** from `SNELLO_DB_KIND`.

Supported values: **`postgresql`** (default), **`mysql`**, **`h2`**, **`mssql`**.

## JDBC URLs

| Kind | Typical URL |
|---|---|
| PostgreSQL | `jdbc:postgresql://host:5432/snello` |
| MySQL | `jdbc:mysql://host:3306/snello` |
| H2 (file) | `jdbc:h2:file:/data/snello;…` (all-in-one default) |
| H2 (mem) | `jdbc:h2:mem:snello;DB_CLOSE_DELAY=-1;MODE=MySQL;…` |
| **SQL Server** | `jdbc:sqlserver://host:1433;databaseName=snello;encrypt=false;trustServerCertificate=true` |

Credentials: `QUARKUS_DATASOURCE_USERNAME` / `QUARKUS_DATASOURCE_PASSWORD` (or the matching `quarkus.datasource.*` properties).

Dev profiles in the app `application.properties` also define `%h2.*` and `%mssql.*` JDBC templates for local re-augmentation.

## Docker / production

```bash
docker run --rm -p 8080:8080 \
  -e SNELLO_DB_KIND=postgresql \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/snello \
  -e QUARKUS_DATASOURCE_USERNAME=snello \
  -e QUARKUS_DATASOURCE_PASSWORD=snello \
  snellocms/snello-control:alpha
```

Swap `SNELLO_DB_KIND` and the JDBC URL for `mysql` or `mssql`. The entrypoint runs `snello-optimize.sh` once per kind (marker file), then starts the JVM.

### SQL Server (`mssql`)

1. Create an empty database (e.g. `snello`) on SQL Server / Azure SQL.
2. Run the control (or query) image with:

```bash
docker run --rm -p 8080:8080 \
  -e SNELLO_DB_KIND=mssql \
  -e QUARKUS_DATASOURCE_JDBC_URL='jdbc:sqlserver://sqlhost:1433;databaseName=snello;encrypt=false;trustServerCertificate=true' \
  -e QUARKUS_DATASOURCE_USERNAME=snello \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  snellocms/snello-control:alpha
```

3. On first start the dialect creates system tables (`metadatas`, `fielddefinitions`, …) using SQL Server types (`nvarchar`, `bit`, `datetime2`, `IDENTITY`).

Notes specific to MSSQL:

- Identifiers are quoted as `[name]`.
- List paging uses `ORDER BY … OFFSET n ROWS FETCH NEXT m ROWS ONLY` (a default `ORDER BY (SELECT NULL)` is added when paging without `_sort`).
- Autoincrement keys / join tables use `INT IDENTITY(1,1)`.
- Case-insensitive filters (`_ilike`, `_icontains`) use `lower(col) LIKE lower(?)` (same on every dialect).
- Column / table renames use `sp_rename`.
- Query-plane session “read-only SQL” is not set for `mssql` (connections still use the read-only JDBC interceptor when the query plane is enabled).

Local optimize without Docker:

```bash
SNELLO_DB_KIND=mssql ./snello-control-app/src/main/scripts/snello-optimize.sh \
  snello-control-app/target/quarkus-app/quarkus-run.jar
```

## Local Quarkus `%dev`

Default remains PostgreSQL via Compose Dev Services (`docker-compose/compose-devservices.yml`). To point at SQL Server instead, disable compose DB (or leave it unused) and set:

```properties
quarkus.datasource.db-kind=mssql
snello.dbtype=mssql
quarkus.datasource.jdbc.url=jdbc:sqlserver://localhost:1433;databaseName=snello;encrypt=false;trustServerCertificate=true
quarkus.datasource.username=snello
quarkus.datasource.password=snello
```

Or use the `%mssql` profile entries already present in the app properties after a matching re-augment.

## Dialect implementation map

| `snello.dbtype` | Package |
|---|---|
| `postgresql` | `…repository.postgresql` |
| `mysql` | `…repository.mysql` |
| `h2` | `…repository.h2` |
| `mssql` | `…repository.mssql` |

Shared DML goes through `SqlQueryBuilder` (quoting differs per dialect).
