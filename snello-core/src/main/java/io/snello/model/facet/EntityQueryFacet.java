package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Properties used to build SQL list/fetch/DDL against a metadata entity.
 */
@RegisterForReflection
public record EntityQueryFacet(
        String table_key,
        String table_key_type,
        String table_key_addition,
        String select_fields,
        String order_by,
        String alias_table,
        String alias_condition,
        String creation_query,
        Boolean api_protected,
        String username_field) {

    public EntityQueryFacet {
        if (api_protected == null) {
            api_protected = false;
        }
    }

    public static EntityQueryFacet empty() {
        return new EntityQueryFacet(null, null, null, null, null, null, null, null, false, null);
    }

    public Builder builder() {
        return new Builder(this);
    }

    public static Builder builder(EntityQueryFacet base) {
        return new Builder(base == null ? empty() : base);
    }

    public static final class Builder {
        public String table_key;
        public String table_key_type;
        public String table_key_addition;
        public String select_fields;
        public String order_by;
        public String alias_table;
        public String alias_condition;
        public String creation_query;
        public Boolean api_protected;
        public String username_field;

        private Builder(EntityQueryFacet base) {
            this.table_key = base.table_key;
            this.table_key_type = base.table_key_type;
            this.table_key_addition = base.table_key_addition;
            this.select_fields = base.select_fields;
            this.order_by = base.order_by;
            this.alias_table = base.alias_table;
            this.alias_condition = base.alias_condition;
            this.creation_query = base.creation_query;
            this.api_protected = base.api_protected;
            this.username_field = base.username_field;
        }

        public EntityQueryFacet build() {
            return new EntityQueryFacet(table_key, table_key_type, table_key_addition, select_fields, order_by,
                    alias_table, alias_condition, creation_query, api_protected, username_field);
        }
    }
}
