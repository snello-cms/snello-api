package io.snello.model;

import java.util.Map;

public class Condition {

    public String uuid;
    public String metadata_uuid;
    public String metadata_name;


    public String separator;
    public String query_params;
    public String condition;
    public String sub_query;


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
