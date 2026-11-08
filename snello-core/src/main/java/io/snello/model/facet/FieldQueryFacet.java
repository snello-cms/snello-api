package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Column / join / search properties used by SQL and query runtime.
 */
@RegisterForReflection
public record FieldQueryFacet(
        String sql_type,
        String sql_definition,
        String default_value,
        Boolean table_key,
        String join_table_name,
        String join_table_key,
        String join_table_select_fields,
        Boolean searchable,
        String search_condition,
        String search_field_name) {

    public FieldQueryFacet {
        if (table_key == null) {
            table_key = false;
        }
        if (searchable == null) {
            searchable = false;
        }
    }

    public static FieldQueryFacet empty() {
        return new FieldQueryFacet(null, null, null, false, null, null, null, false, null, null);
    }

    public Builder builder() {
        return new Builder(this);
    }

    public static Builder builder(FieldQueryFacet base) {
        return new Builder(base == null ? empty() : base);
    }

    public static final class Builder {
        public String sql_type;
        public String sql_definition;
        public String default_value;
        public Boolean table_key;
        public String join_table_name;
        public String join_table_key;
        public String join_table_select_fields;
        public Boolean searchable;
        public String search_condition;
        public String search_field_name;

        private Builder(FieldQueryFacet base) {
            this.sql_type = base.sql_type;
            this.sql_definition = base.sql_definition;
            this.default_value = base.default_value;
            this.table_key = base.table_key;
            this.join_table_name = base.join_table_name;
            this.join_table_key = base.join_table_key;
            this.join_table_select_fields = base.join_table_select_fields;
            this.searchable = base.searchable;
            this.search_condition = base.search_condition;
            this.search_field_name = base.search_field_name;
        }

        public FieldQueryFacet build() {
            return new FieldQueryFacet(sql_type, sql_definition, default_value, table_key, join_table_name,
                    join_table_key, join_table_select_fields, searchable, search_condition, search_field_name);
        }
    }
}
