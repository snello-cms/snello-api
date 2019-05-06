package io.snello.cms.model;

import java.util.HashMap;
import java.util.Map;

public class FieldDefinition {

    public String uuid;
    public String metadata_uuid;
    public String metadata_name;

    public String name;
    public String label;
    // input|button|select|date|radiobutton|checkbox
    public String type;
    // html password, text, number, radio, checkbox, color, date, datetime-local, email, month, number, range, search, tel, time, url, week
    public String inputType;
    // stringa seperata da ","
    public String options;
    // SERVE X RAGGRUPPARE NELLA PAGINA DI EDITING
    public String group_name;
    // SERVE X IL RAGGRUPPAMENTO A WIZARD
    public String tab_name;
    //DOPO VEDREMO COME FARLO
    public String validations;

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

    //definisce se è il campo cercabile nella lista
    public boolean searchable;
    //    static final String EQU = "=";
    //    static final String NE = "_ne";
    //    static final String LT = "_lt";
    //    static final String GT = "_gt";
    //    static final String LTE = "_lte";
    //    static final String GTE = "_gte";
    //    static final String CNT = "_contains";
    //    static final String NCNT = "_ncontains";
    public String searchCondition;
    // composizione del name + la codiione scelta
    // -> es: search on "name": (EQU) name, (LIKE) name_contains,(NOT LIKE) name_ncontains,
    // -> es: search on  "age": (EQU) age, (<) age_lt,(>) age_gt, (<=) age_lte, (>=) age_gte
    public String searchFieldName;

    //definisce se il campo deve essere visto nella lista
    public boolean showInList;




    public FieldDefinition() {
    }

    public FieldDefinition(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public FieldDefinition fromMap(Map<String, Object> map, FieldDefinition fieldDefinition) {
        if (map.get("uuid") instanceof String) {
            fieldDefinition.uuid = (String) map.get("uuid");
        }
        if (map.get("metadata_uuid") instanceof String) {
            fieldDefinition.metadata_uuid = (String) map.get("metadata_uuid");
        }
        if (map.get("metadata_name") instanceof String) {
            fieldDefinition.metadata_name = (String) map.get("metadata_name");
        }

        if (map.get("name") instanceof String) {
            fieldDefinition.name = (String) map.get("name");
        }
        if (map.get("label") instanceof String) {
            fieldDefinition.label = (String) map.get("label");
        }
        if (map.get("type") instanceof String) {
            fieldDefinition.type = (String) map.get("type");
        }
        if (map.get("inputType") instanceof String) {
            fieldDefinition.inputType = (String) map.get("inputType");
        }
        if (map.get("options") instanceof String) {
            fieldDefinition.options = (String) map.get("options");
        }
        if (map.get("group_name") instanceof String) {
            fieldDefinition.group_name = (String) map.get("group_name");
        }
        if (map.get("tab_name") instanceof String) {
            fieldDefinition.tab_name = (String) map.get("tab_name");
        }
        if (map.get("validations") instanceof String) {
            fieldDefinition.metadata_name = (String) map.get("validations");
        }
        if (map.get("table_key") instanceof Boolean) {
            fieldDefinition.table_key = (Boolean) map.get("table_key");
        }
        if (map.get("input_disabled") instanceof Boolean) {
            fieldDefinition.input_disabled = (Boolean) map.get("input_disabled");
        }
        if (map.get("function_def") instanceof String) {
            fieldDefinition.function_def = (String) map.get("function_def");
        }

        if (map.get("sql_type") instanceof String) {
            fieldDefinition.sql_type = (String) map.get("sql_type");
        }
        if (map.get("sql_definition") instanceof String) {
            fieldDefinition.sql_definition = (String) map.get("sql_definition");
        }
        if (map.get("default_value") instanceof String) {
            fieldDefinition.default_value = (String) map.get("default_value");
        }
        if (map.get("pattern") instanceof String) {
            fieldDefinition.pattern = (String) map.get("pattern");
        }
        if (map.get("join_table_name") instanceof String) {
            fieldDefinition.join_table_name = (String) map.get("join_table_name");
        }
        if (map.get("join_table_key") instanceof String) {
            fieldDefinition.join_table_key = (String) map.get("join_table_key");
        }
        if (map.get("join_table_select_fields") instanceof String) {
            fieldDefinition.join_table_select_fields = (String) map.get("join_table_select_fields");
        }

        if (map.get("searchable") instanceof Boolean) {
            fieldDefinition.searchable = (Boolean) map.get("searchable");
        }
        if (map.get("searchCondition") instanceof String) {
            fieldDefinition.searchCondition = (String) map.get("searchCondition");
        }
        if (map.get("searchFieldName") instanceof String) {
            fieldDefinition.searchFieldName = (String) map.get("searchFieldName");
        }

        if (map.get("showInList") instanceof Boolean) {
            fieldDefinition.showInList = (Boolean) map.get("showInList");
        }
        return fieldDefinition;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.uuid != null) {
           map.put("uuid", this.uuid);
        }
        if (this.metadata_uuid != null) {
            map.put("metadata_uuid", this.metadata_uuid);
        }
        if (this.metadata_name != null) {
            map.put("metadata_name", this.metadata_name);
        }
        if (this.name != null) {
            map.put("name", this.name);
        }

        if (this.label != null) {
            map.put("label", this.label);
        }
        if (this.type != null) {
            map.put("type", this.type);
        }
        if (this.inputType != null) {
            map.put("inputType", this.inputType);
        }
        if (this.options != null) {
            map.put("options", this.options);
        }
        if (this.group_name != null) {
            map.put("group_name", this.group_name);
        }
        if (this.tab_name != null) {
            map.put("tab_name", this.tab_name);
        }
        if (this.validations != null) {
            map.put("validations", this.validations);
        }
        map.put("table_key", this.table_key);
        map.put("input_disabled", this.input_disabled);

        if (this.function_def != null) {
            map.put("function_def", this.function_def);
        }
        if (this.sql_type != null) {
            map.put("sql_type", this.sql_type);
        }
        if (this.sql_definition != null) {
            map.put("sql_definition", this.sql_definition);
        }
        if (this.default_value != null) {
            map.put("default_value", this.default_value);
        }
        if (this.pattern != null) {
            map.put("pattern", this.pattern);
        }
        if (this.join_table_name != null) {
            map.put("join_table_name", this.join_table_name);
        }
        if (this.join_table_key != null) {
            map.put("join_table_key", this.join_table_key);
        }
        if (this.join_table_select_fields != null) {
            map.put("join_table_select_fields", this.join_table_select_fields);
        }
            map.put("searchable", this.searchable);
        if (this.searchCondition != null) {
            map.put("searchCondition", this.searchCondition);
        }
        if (this.searchFieldName != null) {
            map.put("searchFieldName", this.searchFieldName);
        }
        map.put("showInList", this.showInList);
        return map;
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "uuid='" + uuid + '\'' +
                ", metadata_uuid='" + metadata_uuid + '\'' +
                ", name='" + name + '\'' +
                ", label='" + label + '\'' +
                ", type='" + type + '\'' +
                ", inputType='" + inputType + '\'' +
                ", options='" + options + '\'' +
                ", group_name='" + group_name + '\'' +
                ", tab_name='" + tab_name + '\'' +
                ", validations='" + validations + '\'' +
                ", table_key=" + table_key +
                ", input_disabled=" + input_disabled +
                ", function_def='" + function_def + '\'' +
                ", sql_type='" + sql_type + '\'' +
                ", sql_definition='" + sql_definition + '\'' +
                ", default_value='" + default_value + '\'' +
                ", join_table_name='" + join_table_name + '\'' +
                ", join_table_key='" + join_table_key + '\'' +
                ", join_table_select_fields='" + join_table_select_fields + '\'' +
                ", pattern='" + pattern + '\'' +
                ", searchable='" + searchable + '\'' +
                ", searchCondition='" + searchCondition + '\'' +
                ", searchFieldName='" + searchFieldName + '\'' +
                ", showInList='" + showInList + '\'' +
                '}';
    }
}
