# All-in-one import fixtures

`metadatas-from-dump.json` is produced from the PostgreSQL dump under `dev/db/` via:

```bash
./dev/scripts/generate-metadatas-export.sh
```

That script restores `compose-test.yml`, boots control against the dump, and calls:

```http
POST /api/metadatas/export
{"all": true}
```

Commit the generated JSON so CI can run `./dev/scripts/run-aio-import-tests.sh` without regenerating (set `REGENERATE_EXPORT=1` to refresh).
