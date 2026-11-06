package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;

/**
 * Immutable query-plane projection of a field definition (no UI / AI properties).
 */
@RegisterForReflection
public record FieldQueryView(
        String name,
        String sqlType,
        String defaultValue,
        boolean tableKey,
        String joinTableName,
        String joinTableKey,
        String joinTableSelectFields,
        boolean searchable,
        String searchCondition,
        String searchFieldName) {

    public static FieldQueryView of(FieldDefinition fd) {
        return new FieldQueryView(fd.name, fd.sql_type, fd.default_value, fd.table_key,
                fd.join_table_name, fd.join_table_key, fd.join_table_select_fields,
                fd.searchable, fd.search_condition, fd.search_field_name);
    }
}
