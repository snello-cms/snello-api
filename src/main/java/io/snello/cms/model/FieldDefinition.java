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


    public String sql_type;
    public String sql_definition;
    public String default_value;
    public String pattern;

    public static String creationQuery = "CREATE TABLE IF NOT EXISTS `fielddefinitions` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `label` varchar(255) COLLATE utf8mb4_unicode_ci  NOT NULL,\n" +
            "  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `inputType` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `options` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `group_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `tab_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `validations` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `table_key` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `input_disabled` BOOLEAN NOT NULL DEFAULT FALSE,\n" +
            "  `function_def` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL ,\n" +
            "  `sql_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `sql_definition` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `default_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `pattern` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";



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


        return fieldDefinition;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> fieldDefinition = new HashMap<>();


        return fieldDefinition;
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
                ", pattern='" + pattern + '\'' +
                '}';
    }
}
