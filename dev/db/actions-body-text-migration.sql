-- Action scripts were capped at 1000 chars (actions.body varchar(1000)).
-- Since 6.0.15-alpha snello widens the column itself at startup (JdbcRepository.upgradeQueries of each dialect);
-- these are the same statements, for running by hand.

-- PostgreSQL
ALTER TABLE actions ALTER COLUMN body TYPE text;
-- H2 (VARCHAR, not CLOB: the driver then returns String)
-- ALTER TABLE actions ALTER COLUMN body SET DATA TYPE VARCHAR(1000000);
-- MySQL
-- ALTER TABLE actions MODIFY body TEXT;
-- SQL Server
-- ALTER TABLE actions ALTER COLUMN body nvarchar(max) NULL;
