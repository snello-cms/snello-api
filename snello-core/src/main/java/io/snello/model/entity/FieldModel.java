package io.snello.model.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;
import io.snello.model.facet.FieldAiFacet;
import io.snello.model.facet.FieldQueryFacet;
import io.snello.model.facet.FieldUiFacet;

import java.util.HashMap;
import java.util.Map;

/**
 * Composed view of a field definition by concern (query / UI / AI).
 * Persists to the same flat {@code fielddefinitions} columns via {@link #toMap()} / {@link #fromMap(Map)}.
 */
@RegisterForReflection
public class FieldModel {

    public String uuid;
    public String metadata_uuid;
    public String metadata_name;
    public String name;

    public FieldQueryFacet query = new FieldQueryFacet();
    public FieldUiFacet ui = new FieldUiFacet();
    public FieldAiFacet ai = new FieldAiFacet();

    public FieldModel() {
    }

    public FieldModel(Map<String, Object> map) {
        fromMap(map);
    }

    public static FieldModel from(FieldDefinition fieldDefinition) {
        FieldModel model = new FieldModel();
        if (fieldDefinition == null) {
            return model;
        }
        model.uuid = fieldDefinition.uuid;
        model.metadata_uuid = fieldDefinition.metadata_uuid;
        model.metadata_name = fieldDefinition.metadata_name;
        model.name = fieldDefinition.name;
        model.query = fieldDefinition.queryFacet();
        model.ui = fieldDefinition.uiFacet();
        model.ai = fieldDefinition.aiFacet();
        return model;
    }

    public FieldDefinition toFieldDefinition() {
        FieldDefinition fieldDefinition = new FieldDefinition();
        applyTo(fieldDefinition);
        return fieldDefinition;
    }

    public void applyTo(FieldDefinition fieldDefinition) {
        if (fieldDefinition == null) {
            return;
        }
        fieldDefinition.uuid = uuid;
        fieldDefinition.metadata_uuid = metadata_uuid;
        fieldDefinition.metadata_name = metadata_name;
        fieldDefinition.name = name;

        fieldDefinition.sql_type = query.sql_type;
        fieldDefinition.sql_definition = query.sql_definition;
        fieldDefinition.default_value = query.default_value;
        fieldDefinition.table_key = query.table_key;
        fieldDefinition.join_table_name = query.join_table_name;
        fieldDefinition.join_table_key = query.join_table_key;
        fieldDefinition.join_table_select_fields = query.join_table_select_fields;
        fieldDefinition.searchable = query.searchable;
        fieldDefinition.search_condition = query.search_condition;
        fieldDefinition.search_field_name = query.search_field_name;

        fieldDefinition.label = ui.label;
        fieldDefinition.type = ui.type;
        fieldDefinition.input_type = ui.input_type;
        fieldDefinition.options = ui.options;
        fieldDefinition.group_name = ui.group_name;
        fieldDefinition.tab_name = ui.tab_name;
        fieldDefinition.validations = ui.validations;
        fieldDefinition.view_index = ui.view_index;
        fieldDefinition.show_in_list = ui.show_in_list;
        fieldDefinition.input_disabled = ui.input_disabled;
        fieldDefinition.function_def = ui.function_def;
        fieldDefinition.pattern = ui.pattern;
        fieldDefinition.mandatory = ui.mandatory;
        fieldDefinition.required = ui.required;
        fieldDefinition.order_num = ui.order_num;

        fieldDefinition.description = ai.description;
        fieldDefinition.ai_profile = ai.toProfileJson();
    }

    public FieldModel fromMap(Map<String, Object> map) {
        if (map == null) {
            return this;
        }
        if (map.get("uuid") instanceof String value) {
            uuid = value;
        }
        if (map.get("metadata_uuid") instanceof String value) {
            metadata_uuid = value;
        }
        if (map.get("metadata_name") instanceof String value) {
            metadata_name = value;
        }
        if (map.get("name") instanceof String value) {
            name = value;
        }

        if (map.get("sql_type") instanceof String value) {
            query.sql_type = value;
        }
        if (map.get("sql_definition") instanceof String value) {
            query.sql_definition = value;
        }
        if (map.get("default_value") instanceof String value) {
            query.default_value = value;
        }
        if (map.get("table_key") instanceof Boolean value) {
            query.table_key = value;
        }
        if (map.get("join_table_name") instanceof String value) {
            query.join_table_name = value;
        }
        if (map.get("join_table_key") instanceof String value) {
            query.join_table_key = value;
        }
        if (map.get("join_table_select_fields") instanceof String value) {
            query.join_table_select_fields = value;
        }
        if (map.get("searchable") instanceof Boolean value) {
            query.searchable = value;
        }
        if (map.get("search_condition") instanceof String value) {
            query.search_condition = value;
        }
        if (map.get("search_field_name") instanceof String value) {
            query.search_field_name = value;
        }

        if (map.get("label") instanceof String value) {
            ui.label = value;
        }
        if (map.get("type") instanceof String value) {
            ui.type = value;
        }
        if (map.get("input_type") instanceof String value) {
            ui.input_type = value;
        }
        if (map.get("options") instanceof String value) {
            ui.options = value;
        }
        if (map.get("group_name") instanceof String value) {
            ui.group_name = value;
        }
        if (map.get("tab_name") instanceof String value) {
            ui.tab_name = value;
        }
        if (map.get("validations") instanceof String value) {
            ui.validations = value;
        }
        if (map.get("view_index") instanceof Number value) {
            ui.view_index = value.intValue();
        }
        if (map.get("show_in_list") instanceof Boolean value) {
            ui.show_in_list = value;
        }
        if (map.get("input_disabled") instanceof Boolean value) {
            ui.input_disabled = value;
        }
        if (map.get("function_def") instanceof String value) {
            ui.function_def = value;
        }
        if (map.get("pattern") instanceof String value) {
            ui.pattern = value;
        }
        if (map.get("mandatory") instanceof Boolean value) {
            ui.mandatory = value;
        }
        if (map.get("required") instanceof Boolean value) {
            ui.required = value;
        }
        if (map.get("order_num") instanceof Number value) {
            ui.order_num = value.intValue();
        }

        ai = FieldAiFacet.of(map.get("description") instanceof String d ? d : null,
                map.get("ai_profile") instanceof String p ? p : null);
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (uuid != null) {
            map.put("uuid", uuid);
        }
        if (metadata_uuid != null) {
            map.put("metadata_uuid", metadata_uuid);
        }
        if (metadata_name != null) {
            map.put("metadata_name", metadata_name);
        }
        if (name != null) {
            map.put("name", name);
        }

        if (query.sql_type != null) {
            map.put("sql_type", query.sql_type);
        }
        if (query.sql_definition != null) {
            map.put("sql_definition", query.sql_definition);
        }
        if (query.default_value != null) {
            map.put("default_value", query.default_value);
        }
        map.put("table_key", query.table_key);
        if (query.join_table_name != null) {
            map.put("join_table_name", query.join_table_name);
        }
        if (query.join_table_key != null) {
            map.put("join_table_key", query.join_table_key);
        }
        if (query.join_table_select_fields != null) {
            map.put("join_table_select_fields", query.join_table_select_fields);
        }
        map.put("searchable", query.searchable);
        if (query.search_condition != null) {
            map.put("search_condition", query.search_condition);
        }
        if (query.search_field_name != null) {
            map.put("search_field_name", query.search_field_name);
        }

        if (ui.label != null) {
            map.put("label", ui.label);
        }
        if (ui.type != null) {
            map.put("type", ui.type);
        }
        if (ui.input_type != null) {
            map.put("input_type", ui.input_type);
        }
        if (ui.options != null) {
            map.put("options", ui.options);
        }
        if (ui.group_name != null) {
            map.put("group_name", ui.group_name);
        }
        if (ui.tab_name != null) {
            map.put("tab_name", ui.tab_name);
        }
        if (ui.validations != null) {
            map.put("validations", ui.validations);
        }
        map.put("view_index", ui.view_index);
        map.put("show_in_list", ui.show_in_list);
        map.put("input_disabled", ui.input_disabled);
        if (ui.function_def != null) {
            map.put("function_def", ui.function_def);
        }
        if (ui.pattern != null) {
            map.put("pattern", ui.pattern);
        }
        // "required" is not a fielddefinitions column (inserting it fails): fold it into mandatory
        map.put("mandatory", ui.mandatory || ui.required);
        map.put("order_num", ui.order_num);

        if (ai.description != null) {
            map.put("description", ai.description);
        }
        String aiProfile = ai.toProfileJson();
        if (aiProfile != null) {
            map.put("ai_profile", aiProfile);
        }
        return map;
    }
}
