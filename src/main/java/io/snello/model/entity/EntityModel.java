package io.snello.model.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.Condition;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.facet.EntityUiFacet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composed view of a metadata entity by concern (identity / query / UI / AI).
 * Persists to the same flat {@code metadatas} columns via {@link #toMap()} / {@link #fromMap(Map)}.
 */
@RegisterForReflection
public class EntityModel {

    public EntityIdentity identity = new EntityIdentity();
    public EntityQueryFacet query = new EntityQueryFacet();
    public EntityUiFacet ui = new EntityUiFacet();
    public EntityAiFacet ai = new EntityAiFacet();

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
        metadata.uuid = identity.uuid;
        metadata.table_name = identity.table_name;
        metadata.metadata_group = identity.metadata_group;
        metadata.already_exist = identity.already_exist;
        metadata.created = identity.created;

        metadata.table_key = query.table_key;
        metadata.table_key_type = query.table_key_type;
        metadata.table_key_addition = query.table_key_addition;
        metadata.select_fields = query.select_fields;
        metadata.order_by = query.order_by;
        metadata.alias_table = query.alias_table;
        metadata.alias_condition = query.alias_condition;
        metadata.creation_query = query.creation_query;
        metadata.api_protected = query.api_protected;
        metadata.username_field = query.username_field;

        metadata.icon = ui.icon;
        metadata.tab_groups = ui.tab_groups;
        metadata.search_fields = ui.search_fields;
        metadata.calendar_enabled = ui.calendar_enabled;
        metadata.calendar_field = ui.calendar_field;
        metadata.calendar_label = ui.calendar_label;

        metadata.description = ai.description;
    }

    public EntityModel fromMap(Map<String, Object> map) {
        if (map == null) {
            return this;
        }
        if (map.get("uuid") instanceof String uuid) {
            identity.uuid = uuid;
        }
        if (map.get("table_name") instanceof String tableName) {
            identity.table_name = tableName;
        }
        if (map.get("metadata_group") instanceof String metadataGroup) {
            identity.metadata_group = metadataGroup;
        }
        if (map.get("already_exist") instanceof Boolean alreadyExist) {
            identity.already_exist = alreadyExist;
        }
        if (map.get("created") instanceof Boolean created) {
            identity.created = created;
        }

        if (map.get("table_key") instanceof String tableKey) {
            query.table_key = tableKey;
        }
        if (map.get("table_key_type") instanceof String tableKeyType) {
            query.table_key_type = tableKeyType;
        }
        if (map.get("table_key_addition") instanceof String tableKeyAddition) {
            query.table_key_addition = tableKeyAddition;
        }
        if (map.get("select_fields") instanceof String selectFields) {
            query.select_fields = selectFields;
        }
        if (map.get("order_by") instanceof String orderBy) {
            query.order_by = orderBy;
        }
        if (map.get("alias_table") instanceof String aliasTable) {
            query.alias_table = aliasTable;
        }
        if (map.get("alias_condition") instanceof String aliasCondition) {
            query.alias_condition = aliasCondition;
        }
        if (map.get("creation_query") instanceof String creationQuery) {
            query.creation_query = creationQuery;
        }
        if (map.get("api_protected") instanceof Boolean apiProtected) {
            query.api_protected = apiProtected;
        }
        if (map.get("username_field") instanceof String usernameField) {
            query.username_field = usernameField;
        }

        if (map.get("icon") instanceof String icon) {
            ui.icon = icon;
        }
        if (map.get("tab_groups") instanceof String tabGroups) {
            ui.tab_groups = tabGroups;
        }
        if (map.get("search_fields") instanceof String searchFields) {
            ui.search_fields = searchFields;
        }
        if (map.get("calendar_enabled") instanceof Boolean calendarEnabled) {
            ui.calendar_enabled = calendarEnabled;
        }
        if (map.get("calendar_field") instanceof String calendarField) {
            ui.calendar_field = calendarField;
        }
        if (map.get("calendar_label") instanceof String calendarLabel) {
            ui.calendar_label = calendarLabel;
        }

        if (map.get("description") instanceof String description) {
            ai.description = description;
        }
        if (map.get("ai_profile") instanceof String aiProfile) {
            ai.ai_profile = aiProfile;
        }
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (identity.uuid != null) {
            map.put("uuid", identity.uuid);
        }
        if (identity.table_name != null) {
            map.put("table_name", identity.table_name);
        }
        if (identity.metadata_group != null) {
            map.put("metadata_group", identity.metadata_group);
        }
        map.put("already_exist", identity.already_exist);
        map.put("created", identity.created);

        if (query.select_fields != null) {
            map.put("select_fields", query.select_fields);
        }
        if (query.alias_table != null) {
            map.put("alias_table", query.alias_table);
        }
        if (query.alias_condition != null) {
            map.put("alias_condition", query.alias_condition);
        }
        if (query.table_key != null) {
            map.put("table_key", query.table_key);
        }
        if (query.table_key_type != null) {
            map.put("table_key_type", query.table_key_type);
        }
        if (query.table_key_addition != null) {
            map.put("table_key_addition", query.table_key_addition);
        }
        if (query.creation_query != null) {
            map.put("creation_query", query.creation_query);
        }
        if (query.order_by != null) {
            map.put("order_by", query.order_by);
        }
        if (query.username_field != null) {
            map.put("username_field", query.username_field);
        }
        map.put("api_protected", query.api_protected);

        if (ui.icon != null) {
            map.put("icon", ui.icon);
        }
        if (ui.tab_groups != null) {
            map.put("tab_groups", ui.tab_groups);
        }
        if (ui.search_fields != null) {
            map.put("search_fields", ui.search_fields);
        }
        map.put("calendar_enabled", ui.calendar_enabled);
        map.put("calendar_field", ui.calendar_field);
        map.put("calendar_label", ui.calendar_label);

        if (ai.description != null) {
            map.put("description", ai.description);
        }
        if (ai.ai_profile != null) {
            map.put("ai_profile", ai.ai_profile);
        }
        return map;
    }
}
