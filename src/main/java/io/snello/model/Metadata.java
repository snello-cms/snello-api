package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.entity.EntityModel;
import io.snello.model.facet.EntityAiFacet;
import io.snello.model.facet.EntityIdentity;
import io.snello.model.facet.EntityQueryFacet;
import io.snello.model.facet.EntityUiFacet;

import java.util.HashMap;
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
    //serve per tabelle preesistenti
    public String alias_table;
    public String alias_condition;


    public String table_key;
    public String table_key_type;
    public String table_key_addition;
    public String creation_query;

    public String order_by;

    public String metadata_group;

    // tab1:group0,group1;tab2:
    public String tab_groups;

    public String icon;

    public List<FieldDefinition> fields;
    public List<Condition> conditions;

    public boolean api_protected;
    public String username_field;

    public boolean calendar_enabled;
    public String calendar_field;
    public String calendar_label;

    // la tabella esiste e non deve essere gestita da SNELLO
    public boolean already_exist;
    //la tabella VA CREATA E GESTISTA DA SNELLO
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
        super();
        fromMap(map, this);
    }

    public EntityIdentity identity() {
        return new EntityIdentity(uuid, table_name, metadata_group, already_exist, created);
    }

    public EntityQueryFacet queryFacet() {
        return new EntityQueryFacet(
                table_key, table_key_type, table_key_addition,
                select_fields, order_by,
                alias_table, alias_condition, creation_query,
                api_protected, username_field
        );
    }

    public EntityUiFacet uiFacet() {
        return new EntityUiFacet(
                icon, tab_groups, search_fields,
                calendar_enabled, calendar_field, calendar_label
        );
    }

    public EntityAiFacet aiFacet() {
        return new EntityAiFacet(description);
    }

    public EntityModel toEntityModel() {
        return EntityModel.from(this);
    }

    @Override
    public String toString() {
        return "Metadata{" +
               "uuid='" + uuid + '\'' +
               ", table_name='" + table_name + '\'' +
               ", select_fields='" + select_fields + '\'' +
               ", search_fields='" + search_fields + '\'' +
               ", description='" + description + '\'' +
               ", alias_table='" + alias_table + '\'' +
               ", alias_condition='" + alias_condition + '\'' +
               ", table_key='" + table_key + '\'' +
               ", table_key_type='" + table_key_type + '\'' +
               ", table_key_addition='" + table_key_addition + '\'' +
               ", creation_query='" + creation_query + '\'' +
               ", order_by='" + order_by + '\'' +
               ", metadata_group='" + metadata_group + '\'' +
               ", tab_groups='" + tab_groups + '\'' +
               ", icon='" + icon + '\'' +
               ", fields='" + fields + '\'' +
               ", conditions='" + conditions + '\'' +
               ", already_exist='" + already_exist + '\'' +
               ", created='" + created + '\'' +
               ", api_protected='" + api_protected + '\'' +
               ", username_field='" + username_field + '\'' +
               ", calendar_enabled='" + calendar_enabled + '\'' +
               ", calendar_field='" + calendar_field + '\'' +
               ", calendar_label='" + calendar_label + '\'' +
               '}';
    }


    public Metadata fromMap(Map<String, Object> map, Metadata metadata) {
        if (map.get("uuid") instanceof String) {
            metadata.uuid = (String) map.get("uuid");
        }
        if (map.get("table_name") instanceof String) {
            metadata.table_name = (String) map.get("table_name");
        }

        if (map.get("select_fields") instanceof String) {
            metadata.select_fields = (String) map.get("select_fields");
        }
        if (map.get("search_fields") instanceof String) {
            metadata.search_fields = (String) map.get("search_fields");
        }
        if (map.get("description") instanceof String) {
            metadata.description = (String) map.get("description");
        }
        if (map.get("alias_table") instanceof String) {
            metadata.alias_table = (String) map.get("alias_table");
        }
        if (map.get("alias_condition") instanceof String) {
            metadata.alias_condition = (String) map.get("alias_condition");
        }
        if (map.get("table_key") instanceof String) {
            metadata.table_key = (String) map.get("table_key");
        }
        if (map.get("table_key_type") instanceof String) {
            metadata.table_key_type = (String) map.get("table_key_type");
        }
        if (map.get("table_key_addition") instanceof String) {
            metadata.table_key_addition = (String) map.get("table_key_addition");
        }
        if (map.get("creation_query") instanceof String) {
            metadata.creation_query = (String) map.get("creation_query");
        }
        if (map.get("order_by") instanceof String) {
            metadata.order_by = (String) map.get("order_by");
        }
        if (map.get("metadata_group") instanceof String) {
            metadata.metadata_group = (String) map.get("metadata_group");
        }
        if (map.get("tab_groups") instanceof String) {
            metadata.tab_groups = (String) map.get("tab_groups");
        }
        if (map.get("icon") instanceof String) {
            metadata.icon = (String) map.get("icon");
        }
        if (map.get("already_exist") instanceof Boolean) {
            metadata.already_exist = (Boolean) map.get("already_exist");
        }
        if (map.get("created") instanceof Boolean) {
            metadata.created = (Boolean) map.get("created");
        }
        if (map.get("api_protected") instanceof Boolean) {
            metadata.api_protected = (Boolean) map.get("api_protected");
        }
        if (map.get("username_field") instanceof String) {
            metadata.username_field = (String) map.get("username_field");
        }
        if (map.get("calendar_enabled") instanceof Boolean) {
            metadata.calendar_enabled = (Boolean) map.get("calendar_enabled");
        }
        if (map.get("calendar_field") instanceof String) {
            metadata.calendar_field = (String) map.get("calendar_field");
        }
        if (map.get("calendar_label") instanceof String) {
            metadata.calendar_label = (String) map.get("calendar_label");
        }
        return metadata;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.uuid != null) {
            map.put("uuid", this.uuid);
        }
        if (this.table_name != null) {
            map.put("table_name", this.table_name);
        }
        if (this.select_fields != null) {
            map.put("select_fields", this.select_fields);
        }
        if (this.search_fields != null) {
            map.put("search_fields", this.search_fields);
        }

        if (this.description != null) {
            map.put("description", this.description);
        }
        if (this.alias_table != null) {
            map.put("alias_table", this.alias_table);
        }
        if (this.alias_condition != null) {
            map.put("alias_condition", this.alias_condition);
        }
        if (this.table_key != null) {
            map.put("table_key", this.table_key);
        }
        if (this.table_key_type != null) {
            map.put("table_key_type", this.table_key_type);
        }
        if (this.table_key_addition != null) {
            map.put("table_key_addition", this.table_key_addition);
        }
        if (this.creation_query != null) {
            map.put("creation_query", this.creation_query);
        }
        if (this.order_by != null) {
            map.put("order_by", this.order_by);
        }
        if (this.metadata_group != null) {
            map.put("metadata_group", this.metadata_group);
        }
        if (this.tab_groups != null) {
            map.put("tab_groups", this.tab_groups);
        }
        if (this.icon != null) {
            map.put("icon", this.icon);
        }
        if (this.username_field != null) {
            map.put("username_field", this.username_field);
        }
        map.put("calendar_field", this.calendar_field);
        map.put("calendar_label", this.calendar_label);
        map.put("calendar_enabled", this.calendar_enabled);
        map.put("already_exist", this.already_exist);
        map.put("created", this.created);
        map.put("api_protected", this.api_protected);

        return map;
    }

}
