package io.snello.model.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.facet.EntityUiFacet;

import java.util.List;
import java.util.Map;

/**
 * Composed view of a metadata entity by concern (identity / query / UI / AI).
 * Persists to the same flat {@code metadatas} columns via {@link #toMap()} / {@link #fromMap(Map)}.
 */
@RegisterForReflection
public class EntityModel {

    public EntityIdentity identity = EntityIdentity.empty();
    public EntityQueryFacet query = EntityQueryFacet.empty();
    public EntityUiFacet ui = EntityUiFacet.empty();
    public EntityAiFacet ai = EntityAiFacet.empty();

    public List<FieldDefinition> fields;
    public List<Condition> conditions;

    public EntityModel() {
    }

    public EntityModel(Map<String, Object> map) {
        fromMap(map);
    }

    public static EntityModel from(Metadata metadata) {
        EntityModel model = new EntityModel();
        if (metadata == null) {
            return model;
        }
        model.identity = metadata.identity();
        model.query = metadata.queryFacet();
        model.ui = metadata.uiFacet();
        model.ai = metadata.aiFacet();
        model.fields = metadata.fields;
        model.conditions = metadata.conditions;
        return model;
    }

    public Metadata toMetadata() {
        Metadata metadata = new Metadata();
        applyTo(metadata);
        metadata.fields = this.fields;
        metadata.conditions = this.conditions;
        return metadata;
    }

    public void applyTo(Metadata metadata) {
        if (metadata == null) {
            return;
        }
        metadata.applyFacets(identity, query, ui, ai);
    }

    public EntityModel fromMap(Map<String, Object> map) {
        if (map == null) {
            return this;
        }
        identity = new EntityIdentity(
                MapReads.str(map, "uuid"),
                MapReads.str(map, "table_name"),
                MapReads.str(map, "metadata_group"),
                MapReads.bool(map, "already_exist", false),
                MapReads.bool(map, "created", false));
        query = new EntityQueryFacet(
                MapReads.str(map, "table_key"),
                MapReads.str(map, "table_key_type"),
                MapReads.str(map, "table_key_addition"),
                MapReads.str(map, "select_fields"),
                MapReads.str(map, "order_by"),
                MapReads.str(map, "alias_table"),
                MapReads.str(map, "alias_condition"),
                MapReads.str(map, "creation_query"),
                MapReads.bool(map, "api_protected", false),
                MapReads.str(map, "username_field"));
        ui = new EntityUiFacet(
                MapReads.str(map, "icon"),
                MapReads.str(map, "tab_groups"),
                MapReads.str(map, "search_fields"),
                MapReads.bool(map, "calendar_enabled", false),
                MapReads.str(map, "calendar_field"),
                MapReads.str(map, "calendar_label"));
        ai = EntityAiFacet.of(MapReads.str(map, "description"), MapReads.str(map, "ai_profile"));
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", identity.uuid());
        MapWrites.put(map, "table_name", identity.table_name());
        MapWrites.put(map, "metadata_group", identity.metadata_group());
        MapWrites.putBool(map, "already_exist", identity.already_exist());
        MapWrites.putBool(map, "created", identity.created());

        MapWrites.put(map, "select_fields", query.select_fields());
        MapWrites.put(map, "alias_table", query.alias_table());
        MapWrites.put(map, "alias_condition", query.alias_condition());
        MapWrites.put(map, "table_key", query.table_key());
        MapWrites.put(map, "table_key_type", query.table_key_type());
        MapWrites.put(map, "table_key_addition", query.table_key_addition());
        MapWrites.put(map, "creation_query", query.creation_query());
        MapWrites.put(map, "order_by", query.order_by());
        MapWrites.put(map, "username_field", query.username_field());
        MapWrites.putBool(map, "api_protected", query.api_protected());

        MapWrites.put(map, "icon", ui.icon());
        MapWrites.put(map, "tab_groups", ui.tab_groups());
        MapWrites.put(map, "search_fields", ui.search_fields());
        MapWrites.putBool(map, "calendar_enabled", ui.calendar_enabled());
        map.put("calendar_field", ui.calendar_field());
        map.put("calendar_label", ui.calendar_label());

        MapWrites.put(map, "description", ai.description());
        MapWrites.put(map, "ai_profile", ai.toProfileJson());
        return map;
    }
}
