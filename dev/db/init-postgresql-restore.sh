#!/bin/sh
set -e

ARCHIVE="/tmp/backup_04_04_2026.backup.sql"

echo "[postgres-init] Restoring dump from ${ARCHIVE}"
pg_restore \
  --no-owner \
  --no-privileges \
  --clean \
  --if-exists \
  --verbose \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  "${ARCHIVE}"

echo "[postgres-init] Restore completed"
