package io.snello.cms.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Metadata {

    public String uuid;
    public String table_name;
    public String select_fields;
    public String description;
    //serve per tabelle preesistenti
    public String alias_table;
    public String alias_condition;


    public String table_key;
    public String table_key_type;
    public String table_key_addition;
    public String creation_query;

    public String order_by;

    public List<FieldDefinition> fields;
    public List<Condition> conditions;

    public static String creationQuery = "CREATE TABLE IF NOT EXISTS `metadatas` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `table_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `select_fields` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `alias_table` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `alias_condition` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `table_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `table_key_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `table_key_addition` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `creation_query` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `order_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

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


    @Override
    public String toString() {
        return "Metadata{" +
                "uuid='" + uuid + '\'' +
                ", table_name='" + table_name + '\'' +
                ", select_fields='" + select_fields + '\'' +
                ", alias_table='" + alias_table + '\'' +
                ", creation_query='" + creation_query + '\'' +
                ", alias_condition='" + alias_condition + '\'' +
                ", table_key='" + table_key + '\'' +
                ", table_key_type='" + table_key_type + '\'' +
                ", table_key_addition='" + table_key_addition + '\'' +
                ", order_by='" + order_by + '\'' +
                ", fields=" + fields +
                ", conditions=" + conditions +
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
        return metadata;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new HashMap<>();


        return metadata;
    }

}
