package io.snello.model;

import java.util.HashMap;
import java.util.Map;

public class Condition {

    public String uuid;
    public String metadata_uuid;
    public String metadata_name;


    public String separator;
    public String query_params;
    public String condition;
    public String sub_query;
    // LE CONDITIONS WITH THIS FIELD VALORIZED ARE IMMODIFIABLE
    public String metadata_multijoin_uuid;


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
        if (map.get("metadata_multijoin_uuid") instanceof String) {
            condition.metadata_multijoin_uuid = (String) map.get("metadata_multijoin_uuid");
        }

        return condition;
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
        if (this.separator != null) {
            map.put("separator", this.separator);
        }
        if (this.query_params != null) {
            map.put("query_params", this.query_params);
        }
        if (this.condition != null) {
            map.put("condition", this.condition);
        }
        if (this.sub_query != null) {
            map.put("sub_query", this.sub_query);
        }
        if (this.metadata_multijoin_uuid != null) {
            map.put("metadata_multijoin_uuid", this.metadata_multijoin_uuid);
        }
        return map;
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
                ", metadata_multijoin_uuid='" + metadata_multijoin_uuid + '\'' +
                '}';
    }
}
