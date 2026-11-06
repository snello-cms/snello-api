package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;

import java.util.List;

/**
 * Immutable query-plane projection of a metadata entity: everything list/fetch and row-level security need,
 * nothing the SPA or the AI tools need. Unlike the cached {@link Metadata}, it can be shared across threads
 * safely.
 */
@RegisterForReflection
public record EntityQueryView(
        String tableName,
        String tableKey,
        String tableKeyType,
        String tableKeyAddition,
        String selectFields,
        String orderBy,
        String aliasTable,
        String aliasCondition,
        boolean apiProtected,
        String usernameField,
        List<FieldQueryView> fields) {

    public static EntityQueryView of(Metadata m, List<FieldDefinition> fieldDefinitions) {
        List<FieldQueryView> fields = fieldDefinitions == null
                ? List.of()
                : fieldDefinitions.stream().map(FieldQueryView::of).toList();
        return new EntityQueryView(m.table_name, m.table_key, m.table_key_type, m.table_key_addition,
                m.select_fields, m.order_by, m.alias_table, m.alias_condition,
                m.api_protected, m.username_field, fields);
    }

    public boolean hasAlias() {
        return aliasTable != null && !aliasTable.isBlank();
    }

    /** Physical table actually queried ({@code alias_table} redirect wins). */
    public String physicalTable() {
        return hasAlias() ? aliasTable : tableName;
    }

    /** Row-level filtering is active only when the entity is protected and names its owner column. */
    public boolean rowLevelSecured() {
        return apiProtected && usernameField != null && !usernameField.isBlank();
    }
}
