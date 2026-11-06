package io.snello.model.pojo;

/**
 * Storage view of one field: everything DDL needs, nothing the UI owns.
 *
 * @param name          column name
 * @param kind          resolved column type; null when it cannot be resolved (DDL then fails with {@code sourceType})
 * @param sqlDefinition raw column SQL that overrides {@code kind} when set
 * @param multiJoin     the field is stored in a {@code <table>_<join_table_name>} join table
 * @param joinTableName referenced entity (join / multijoin)
 * @param joinTableKey  key of the referenced entity
 * @param sourceType    what the kind was resolved from, for error messages
 */
public record ColumnSpec(String name, ColumnKind kind, String sqlDefinition, boolean multiJoin,
                         String joinTableName, String joinTableKey, String sourceType) {

    public boolean hasSqlDefinition() {
        return sqlDefinition != null && !sqlDefinition.trim().isEmpty();
    }
}
