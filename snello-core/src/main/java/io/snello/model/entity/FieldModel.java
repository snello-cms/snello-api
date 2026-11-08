package io.snello.model.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.FieldDefinition;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;
import io.snello.model.facet.FieldAiFacet;
import io.snello.model.facet.FieldQueryFacet;
import io.snello.model.facet.FieldUiFacet;

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

    public FieldQueryFacet query = FieldQueryFacet.empty();
    public FieldUiFacet ui = FieldUiFacet.empty();
    public FieldAiFacet ai = FieldAiFacet.empty();

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
        fieldDefinition.applyFacets(uuid, metadata_uuid, metadata_name, name, query, ui, ai);
    }

    public FieldModel fromMap(Map<String, Object> map) {
        if (map == null) {
            return this;
        }
        uuid = MapReads.str(map, "uuid");
        metadata_uuid = MapReads.str(map, "metadata_uuid");
        metadata_name = MapReads.str(map, "metadata_name");
        name = MapReads.str(map, "name");
        query = new FieldQueryFacet(
                MapReads.str(map, "sql_type"),
                MapReads.str(map, "sql_definition"),
                MapReads.str(map, "default_value"),
                MapReads.bool(map, "table_key", false),
                MapReads.str(map, "join_table_name"),
                MapReads.str(map, "join_table_key"),
                MapReads.str(map, "join_table_select_fields"),
                MapReads.bool(map, "searchable", false),
                MapReads.str(map, "search_condition"),
                MapReads.str(map, "search_field_name"));
        ui = new FieldUiFacet(
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
                MapReads.intVal(map, "order_num", 0));
        ai = FieldAiFacet.of(MapReads.str(map, "description"), MapReads.str(map, "ai_profile"));
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "metadata_uuid", metadata_uuid);
        MapWrites.put(map, "metadata_name", metadata_name);
        MapWrites.put(map, "name", name);

        MapWrites.put(map, "sql_type", query.sql_type());
        MapWrites.put(map, "sql_definition", query.sql_definition());
        MapWrites.put(map, "default_value", query.default_value());
        MapWrites.putBool(map, "table_key", query.table_key());
        MapWrites.put(map, "join_table_name", query.join_table_name());
        MapWrites.put(map, "join_table_key", query.join_table_key());
        MapWrites.put(map, "join_table_select_fields", query.join_table_select_fields());
        MapWrites.putBool(map, "searchable", query.searchable());
        MapWrites.put(map, "search_condition", query.search_condition());
        MapWrites.put(map, "search_field_name", query.search_field_name());

        MapWrites.put(map, "label", ui.label());
        MapWrites.put(map, "type", ui.type());
        MapWrites.put(map, "input_type", ui.input_type());
        MapWrites.put(map, "options", ui.options());
        MapWrites.put(map, "group_name", ui.group_name());
        MapWrites.put(map, "tab_name", ui.tab_name());
        MapWrites.put(map, "validations", ui.validations());
        MapWrites.putInt(map, "view_index", ui.view_index());
        MapWrites.putBool(map, "show_in_list", ui.show_in_list());
        MapWrites.putBool(map, "input_disabled", ui.input_disabled());
        MapWrites.put(map, "function_def", ui.function_def());
        MapWrites.put(map, "pattern", ui.pattern());
        MapWrites.putBool(map, "mandatory", ui.mandatory() || ui.required());
        MapWrites.putInt(map, "order_num", ui.order_num());

        MapWrites.put(map, "description", ai.description());
        MapWrites.put(map, "ai_profile", ai.toProfileJson());
        return map;
    }
}
