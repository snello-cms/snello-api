package io.snello.model;

import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.entity.FieldModel;
import io.snello.model.facet.FieldAiFacet;
import io.snello.model.facet.FieldQueryFacet;
import io.snello.model.facet.FieldUiFacet;

import java.util.Map;

/**
 * Legacy flat field DTO (maps 1:1 to {@code fielddefinitions}).
 * Prefer {@link FieldModel} / facet views for new code; this class remains the runtime facade.
 */
@RegisterForReflection
public class FieldDefinition {

    public String uuid;
    public String metadata_uuid;
    public String metadata_name;

    public String name;
    public String label;
    public String description;
    /** JSON column: hint, semantic_type, examples (see {@link io.snello.model.pojo.FieldAiProfile}). */
    public String ai_profile;
    public String type;
    public String input_type;
    public String options;
    public String group_name;
    public String tab_name;
    public String validations;
    public int view_index;

    public boolean table_key;
    public boolean input_disabled;
    public String function_def;

    public String join_table_name;
    public String join_table_key;
    public String join_table_select_fields;

    public String sql_type;
    public String sql_definition;
    public String default_value;
    public String pattern;

    public boolean searchable;
    public String search_condition;
    public String search_field_name;

    public boolean show_in_list;

    public boolean mandatory;
    public boolean required;
    public int order_num;

    public FieldDefinition() {
    }

    public FieldDefinition(Map<String, Object> map) {
        fromMap(map, this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public FieldQueryFacet queryFacet() {
        return new FieldQueryFacet(
                sql_type, sql_definition, default_value, table_key,
                join_table_name, join_table_key, join_table_select_fields,
                searchable, search_condition, search_field_name);
    }

    public FieldUiFacet uiFacet() {
        return new FieldUiFacet(
                label, type, input_type, options, group_name, tab_name, validations, view_index,
                show_in_list, input_disabled, function_def, pattern, mandatory, required, order_num);
    }

    public FieldAiFacet aiFacet() {
        return FieldAiFacet.of(description, ai_profile);
    }

    public FieldModel toFieldModel() {
        return FieldModel.from(this);
    }

    public void applyFacets(String uuid, String metadataUuid, String metadataName, String name,
            FieldQueryFacet query, FieldUiFacet ui, FieldAiFacet ai) {
        this.uuid = uuid;
        this.metadata_uuid = metadataUuid;
        this.metadata_name = metadataName;
        this.name = name;
        if (query != null) {
            this.sql_type = query.sql_type();
            this.sql_definition = query.sql_definition();
            this.default_value = query.default_value();
            this.table_key = query.table_key();
            this.join_table_name = query.join_table_name();
            this.join_table_key = query.join_table_key();
            this.join_table_select_fields = query.join_table_select_fields();
            this.searchable = query.searchable();
            this.search_condition = query.search_condition();
            this.search_field_name = query.search_field_name();
        }
        if (ui != null) {
            this.label = ui.label();
            this.type = ui.type();
            this.input_type = ui.input_type();
            this.options = ui.options();
            this.group_name = ui.group_name();
            this.tab_name = ui.tab_name();
            this.validations = ui.validations();
            this.view_index = ui.view_index();
            this.show_in_list = ui.show_in_list();
            this.input_disabled = ui.input_disabled();
            this.function_def = ui.function_def();
            this.pattern = ui.pattern();
            this.mandatory = ui.mandatory();
            this.required = ui.required();
            this.order_num = ui.order_num();
        }
        if (ai != null) {
            this.description = ai.description();
            this.ai_profile = ai.toProfileJson();
        }
    }

    public FieldDefinition fromMap(Map<String, Object> map, FieldDefinition fieldDefinition) {
        if (map == null || fieldDefinition == null) {
            return fieldDefinition;
        }
        fieldDefinition.applyFacets(
                MapReads.str(map, "uuid"),
                MapReads.str(map, "metadata_uuid"),
                MapReads.str(map, "metadata_name"),
                MapReads.str(map, "name"),
                new FieldQueryFacet(
                        MapReads.str(map, "sql_type"),
                        MapReads.str(map, "sql_definition"),
                        MapReads.str(map, "default_value"),
                        MapReads.bool(map, "table_key", false),
                        MapReads.str(map, "join_table_name"),
                        MapReads.str(map, "join_table_key"),
                        MapReads.str(map, "join_table_select_fields"),
                        MapReads.bool(map, "searchable", false),
                        MapReads.str(map, "search_condition"),
                        MapReads.str(map, "search_field_name")),
                new FieldUiFacet(
                        MapReads.str(map, "label"),
                        MapReads.str(map, "type"),
                        MapReads.str(map, "input_type"),
                        MapReads.str(map, "options"),
                        MapReads.str(map, "group_name"),
                        MapReads.str(map, "tab_name"),
                        MapReads.str(map, "validations"),
                        MapReads.intVal(map, "view_index", 0),
                        MapReads.bool(map, "show_in_list", false),
                        MapReads.bool(map, "input_disabled", false),
                        MapReads.str(map, "function_def"),
                        MapReads.str(map, "pattern"),
                        MapReads.bool(map, "mandatory", false),
                        MapReads.bool(map, "required", false),
                        MapReads.intVal(map, "order_num", 0)),
                FieldAiFacet.of(MapReads.str(map, "description"), MapReads.str(map, "ai_profile")));
        String rawProfile = MapReads.str(map, "ai_profile");
        if (rawProfile != null) {
            fieldDefinition.ai_profile = rawProfile;
        }
        fieldDefinition.description = MapReads.str(map, "description");
        return fieldDefinition;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "metadata_uuid", metadata_uuid);
        MapWrites.put(map, "metadata_name", metadata_name);
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "label", label);
        MapWrites.put(map, "description", description);
        MapWrites.put(map, "ai_profile", ai_profile);
        MapWrites.put(map, "type", type);
        MapWrites.put(map, "input_type", input_type);
        MapWrites.put(map, "options", options);
        MapWrites.put(map, "group_name", group_name);
        MapWrites.put(map, "tab_name", tab_name);
        MapWrites.put(map, "validations", validations);
        MapWrites.putBool(map, "table_key", table_key);
        MapWrites.putBool(map, "input_disabled", input_disabled);
        MapWrites.put(map, "function_def", function_def);
        MapWrites.put(map, "sql_type", sql_type);
        MapWrites.put(map, "sql_definition", sql_definition);
        MapWrites.put(map, "default_value", default_value);
        MapWrites.put(map, "pattern", pattern);
        MapWrites.put(map, "join_table_name", join_table_name);
        MapWrites.put(map, "join_table_key", join_table_key);
        MapWrites.put(map, "join_table_select_fields", join_table_select_fields);
        MapWrites.putBool(map, "searchable", searchable);
        MapWrites.put(map, "search_condition", search_condition);
        MapWrites.put(map, "search_field_name", search_field_name);
        MapWrites.putBool(map, "show_in_list", show_in_list);
        MapWrites.putInt(map, "view_index", view_index);
        MapWrites.putBool(map, "mandatory", mandatory || required);
        MapWrites.putInt(map, "order_num", order_num);
        return map;
    }

    @Override
    public String toString() {
        return "FieldDefinition{uuid='" + uuid + "', name='" + name + "'}";
    }

    public static final class Builder {
        private String uuid;
        private String metadata_uuid;
        private String metadata_name;
        private String name;
        private FieldQueryFacet query = FieldQueryFacet.empty();
        private FieldUiFacet ui = FieldUiFacet.empty();
        private FieldAiFacet ai = FieldAiFacet.empty();

        public Builder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder metadataUuid(String metadataUuid) {
            this.metadata_uuid = metadataUuid;
            return this;
        }

        public Builder metadataName(String metadataName) {
            this.metadata_name = metadataName;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder query(FieldQueryFacet query) {
            this.query = query == null ? FieldQueryFacet.empty() : query;
            return this;
        }

        public Builder ui(FieldUiFacet ui) {
            this.ui = ui == null ? FieldUiFacet.empty() : ui;
            return this;
        }

        public Builder ai(FieldAiFacet ai) {
            this.ai = ai == null ? FieldAiFacet.empty() : ai;
            return this;
        }

        public FieldDefinition build() {
            FieldDefinition field = new FieldDefinition();
            field.applyFacets(uuid, metadata_uuid, metadata_name, name, query, ui, ai);
            return field;
        }
    }
}
