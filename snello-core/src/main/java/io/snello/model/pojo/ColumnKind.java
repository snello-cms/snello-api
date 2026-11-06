package io.snello.model.pojo;

/**
 * Dialect-neutral physical column type. Resolved once per field by {@link io.snello.util.ColumnSpecs}; each
 * {@code JdbcRepository} only maps a kind to its own SQL.
 */
public enum ColumnKind {
    /** short free text: varchar(200) */
    SHORT_TEXT,
    /** references, lists, select values: varchar(2048) */
    MEDIUM_TEXT,
    /** unbounded text: text / VARCHAR */
    LONG_TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME,
    TIME
}
