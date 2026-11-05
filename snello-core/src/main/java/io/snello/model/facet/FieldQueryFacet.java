package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Column / join / search properties used by SQL and query runtime.
 */
@RegisterForReflection
public class FieldQueryFacet {

    public String sql_type;
    public String sql_definition;
    public String default_value;
    public boolean table_key;
    public String join_table_name;
    public String join_table_key;
    public String join_table_select_fields;
    public boolean searchable;
    public String search_condition;
    public String search_field_name;

    public FieldQueryFacet() {
    }
}
