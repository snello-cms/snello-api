-- Add phase to actions and backfill existing rows.
-- Target: PostgreSQL.

ALTER TABLE actions
ADD COLUMN IF NOT EXISTS phase varchar(10) DEFAULT 'POST';

UPDATE actions
SET phase = 'POST'
WHERE phase IS NULL OR TRIM(phase) = '';
