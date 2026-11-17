-- Action scripts were capped at 1000 chars (actions.body varchar(1000)); new databases create it as text.
-- Existing databases: run the line for your dialect once.

-- PostgreSQL
ALTER TABLE actions ALTER COLUMN body TYPE text;
-- H2
-- ALTER TABLE actions ALTER COLUMN body SET DATA TYPE CLOB;
-- MySQL
-- ALTER TABLE actions MODIFY body TEXT;
