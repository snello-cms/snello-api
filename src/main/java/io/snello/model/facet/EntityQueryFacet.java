package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Properties used to build SQL list/fetch/DDL against a metadata entity.
 */
@RegisterForReflection
public class EntityQueryFacet {

    public String table_key;
    public String table_key_type;
    public String table_key_addition;
    public String select_fields;
    public String order_by;
    public String alias_table;
    public String alias_condition;
    public String creation_query;
    public boolean api_protected;
    public String username_field;

    public EntityQueryFacet() {
    }

    public EntityQueryFacet(String table_key, String table_key_type, String table_key_addition,
                            String select_fields, String order_by,
                            String alias_table, String alias_condition, String creation_query,
                            boolean api_protected, String username_field) {
        this.table_key = table_key;
        this.table_key_type = table_key_type;
        this.table_key_addition = table_key_addition;
        this.select_fields = select_fields;
        this.order_by = order_by;
        this.alias_table = alias_table;
        this.alias_condition = alias_condition;
        this.creation_query = creation_query;
        this.api_protected = api_protected;
        this.username_field = username_field;
    }
}
