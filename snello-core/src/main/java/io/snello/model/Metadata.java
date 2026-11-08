package io.snello.model;

import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.entity.EntityModel;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.facet.EntityUiFacet;

import java.util.List;
import java.util.Map;

/**
 * Legacy flat metadata DTO (maps 1:1 to {@code metadatas}).
 * Prefer {@link EntityModel} / facet views for new code; this class remains the runtime facade.
 */
@RegisterForReflection
public class Metadata {

    public String uuid;
    public String table_name;
    public String select_fields;
    public String search_fields;
    public String description;
    /** JSON column: summary, synonyms, tool_visible, examples (see {@link io.snello.model.pojo.EntityAiProfile}). */
    public String ai_profile;
    public String alias_table;
    public String alias_condition;

    public String table_key;
    public String table_key_type;
    public String table_key_addition;
    public String creation_query;

    public String order_by;

    public String metadata_group;

    public String tab_groups;

    public String icon;

    public List<FieldDefinition> fields;
    public List<Condition> conditions;

    public boolean api_protected;
    public String username_field;

    public boolean calendar_enabled;
    public String calendar_field;
    public String calendar_label;

    public boolean already_exist;
    public boolean created;

    public Metadata(String table_name, String table_key, String creation_query, String order_by) {
        this.table_name = table_name;
        this.table_key = table_key;
        this.creation_query = creation_query;
        this.order_by = order_by;
    }

    public Metadata() {
    }

    public Metadata(Map<String, Object> map) {
        fromMap(map, this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public EntityIdentity identity() {
        return new EntityIdentity(uuid, table_name, metadata_group, already_exist, created);
    }

    public EntityQueryFacet queryFacet() {
        return new EntityQueryFacet(
                table_key, table_key_type, table_key_addition,
                select_fields, order_by,
                alias_table, alias_condition, creation_query,
                api_protected, username_field);
    }

    public EntityUiFacet uiFacet() {
        return new EntityUiFacet(
                icon, tab_groups, search_fields,
                calendar_enabled, calendar_field, calendar_label);
    }

    public EntityAiFacet aiFacet() {
        return EntityAiFacet.of(description, ai_profile);
    }

    public EntityModel toEntityModel() {
        return EntityModel.from(this);
    }

    public void applyFacets(EntityIdentity identity, EntityQueryFacet query, EntityUiFacet ui, EntityAiFacet ai) {
        if (identity != null) {
            this.uuid = identity.uuid();
            this.table_name = identity.table_name();
            this.metadata_group = identity.metadata_group();
            this.already_exist = identity.already_exist();
            this.created = identity.created();
        }
        if (query != null) {
            this.table_key = query.table_key();
            this.table_key_type = query.table_key_type();
            this.table_key_addition = query.table_key_addition();
            this.select_fields = query.select_fields();
            this.order_by = query.order_by();
            this.alias_table = query.alias_table();
            this.alias_condition = query.alias_condition();
            this.creation_query = query.creation_query();
            this.api_protected = query.api_protected();
            this.username_field = query.username_field();
        }
        if (ui != null) {
            this.icon = ui.icon();
            this.tab_groups = ui.tab_groups();
            this.search_fields = ui.search_fields();
            this.calendar_enabled = ui.calendar_enabled();
            this.calendar_field = ui.calendar_field();
            this.calendar_label = ui.calendar_label();
        }
        if (ai != null) {
            this.description = ai.description();
            this.ai_profile = ai.toProfileJson();
        }
    }

    public Metadata fromMap(Map<String, Object> map, Metadata metadata) {
        if (map == null || metadata == null) {
            return metadata;
        }
        metadata.applyFacets(
                new EntityIdentity(
                        MapReads.str(map, "uuid"),
                        MapReads.str(map, "table_name"),
                        MapReads.str(map, "metadata_group"),
                        MapReads.bool(map, "already_exist", false),
                        MapReads.bool(map, "created", false)),
                new EntityQueryFacet(
                        MapReads.str(map, "table_key"),
                        MapReads.str(map, "table_key_type"),
                        MapReads.str(map, "table_key_addition"),
                        MapReads.str(map, "select_fields"),
                        MapReads.str(map, "order_by"),
                        MapReads.str(map, "alias_table"),
                        MapReads.str(map, "alias_condition"),
                        MapReads.str(map, "creation_query"),
                        MapReads.bool(map, "api_protected", false),
                        MapReads.str(map, "username_field")),
                new EntityUiFacet(
                        MapReads.str(map, "icon"),
                        MapReads.str(map, "tab_groups"),
                        MapReads.str(map, "search_fields"),
                        MapReads.bool(map, "calendar_enabled", false),
                        MapReads.str(map, "calendar_field"),
                        MapReads.str(map, "calendar_label")),
                EntityAiFacet.of(MapReads.str(map, "description"), MapReads.str(map, "ai_profile")));
        // keep raw ai_profile column when present (of() re-serializes; preserve DB text if set)
        String rawProfile = MapReads.str(map, "ai_profile");
        if (rawProfile != null) {
            metadata.ai_profile = rawProfile;
        }
        metadata.description = MapReads.str(map, "description");
        return metadata;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "table_name", table_name);
        MapWrites.put(map, "select_fields", select_fields);
        MapWrites.put(map, "search_fields", search_fields);
        MapWrites.put(map, "description", description);
        MapWrites.put(map, "ai_profile", ai_profile);
        MapWrites.put(map, "alias_table", alias_table);
        MapWrites.put(map, "alias_condition", alias_condition);
        MapWrites.put(map, "table_key", table_key);
        MapWrites.put(map, "table_key_type", table_key_type);
        MapWrites.put(map, "table_key_addition", table_key_addition);
        MapWrites.put(map, "creation_query", creation_query);
        MapWrites.put(map, "order_by", order_by);
        MapWrites.put(map, "metadata_group", metadata_group);
        MapWrites.put(map, "tab_groups", tab_groups);
        MapWrites.put(map, "icon", icon);
        MapWrites.put(map, "username_field", username_field);
        map.put("calendar_field", calendar_field);
        map.put("calendar_label", calendar_label);
        MapWrites.putBool(map, "calendar_enabled", calendar_enabled);
        MapWrites.putBool(map, "already_exist", already_exist);
        MapWrites.putBool(map, "created", created);
        MapWrites.putBool(map, "api_protected", api_protected);
        return map;
    }

    @Override
    public String toString() {
        return "Metadata{uuid='" + uuid + "', table_name='" + table_name + "'}";
    }

    public static final class Builder {
        private EntityIdentity identity = EntityIdentity.empty();
        private EntityQueryFacet query = EntityQueryFacet.empty();
        private EntityUiFacet ui = EntityUiFacet.empty();
        private EntityAiFacet ai = EntityAiFacet.empty();
        private List<FieldDefinition> fields;
        private List<Condition> conditions;

        public Builder identity(EntityIdentity identity) {
            this.identity = identity == null ? EntityIdentity.empty() : identity;
            return this;
        }

        public Builder query(EntityQueryFacet query) {
            this.query = query == null ? EntityQueryFacet.empty() : query;
            return this;
        }

        public Builder ui(EntityUiFacet ui) {
            this.ui = ui == null ? EntityUiFacet.empty() : ui;
            return this;
        }

        public Builder ai(EntityAiFacet ai) {
            this.ai = ai == null ? EntityAiFacet.empty() : ai;
            return this;
        }

        public Builder fields(List<FieldDefinition> fields) {
            this.fields = fields;
            return this;
        }

        public Builder conditions(List<Condition> conditions) {
            this.conditions = conditions;
            return this;
        }

        public Metadata build() {
            Metadata metadata = new Metadata();
            metadata.applyFacets(identity, query, ui, ai);
            metadata.fields = fields;
            metadata.conditions = conditions;
            return metadata;
        }
    }
}
