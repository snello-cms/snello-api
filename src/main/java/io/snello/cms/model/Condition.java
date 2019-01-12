package io.snello.cms.model;

import java.util.Map;

public class Condition {

    public String uuid;
    public String metadata_uuid;
    public String metadata_name;


    public String separator;
    public String query_params;
    public String condition;
    public String sub_query;

    public static String creationQuery = "CREATE TABLE IF NOT EXISTS `conditions` (\n" +
            "  `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `metadata_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `separator` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,\n" +
            "  `condition` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `query_params` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  `sub_query` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
            "  PRIMARY KEY (uuid)\n" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    public Condition() {
    }

    public Condition(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public Condition fromMap(Map<String, Object> map, Condition condition) {
        if (map.get("uuid") instanceof String) {
            condition.uuid = (String) map.get("uuid");
        }
        if (map.get("metadata_uuid") instanceof String) {
            condition.metadata_uuid = (String) map.get("metadata_uuid");
        }
        if (map.get("metadata_name") instanceof String) {
            condition.metadata_name = (String) map.get("metadata_name");
        }

        if (map.get("separator") instanceof String) {
            condition.separator = (String) map.get("separator");
        }
        if (map.get("query_params") instanceof String) {
            condition.query_params = (String) map.get("query_params");
        }
        if (map.get("condition") instanceof String) {
            condition.condition = (String) map.get("condition");
        }
        if (map.get("sub_query") instanceof String) {
            condition.sub_query = (String) map.get("sub_query");
        }

        return condition;
    }



    @Override
    public String toString() {
        return "Condition{" +
                "uuid='" + uuid + '\'' +
                ", metadata_uuid='" + metadata_uuid + '\'' +
                ", metadata_name='" + metadata_name + '\'' +
                ", separator='" + separator + '\'' +
                ", query_params='" + query_params + '\'' +
                ", condition='" + condition + '\'' +
                ", sub_query='" + sub_query + '\'' +
                '}';
    }
}
