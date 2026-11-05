package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public class SelectQuery {

    public String uuid;
    public String query_name;
    public String select_query;
    public String select_query_count;
    public boolean with_params;

    public SelectQuery(String query_name, String select_query, String select_query_count, boolean with_params) {
        this.query_name = query_name;
        this.select_query = select_query;
        this.select_query_count = select_query_count;
        this.with_params = with_params;
    }

    public SelectQuery() {
    }

    public SelectQuery(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }


    @Override
    public String toString() {
        return "SelectQuery{" +
                "uuid='" + uuid + '\'' +
                ", query_name='" + query_name + '\'' +
                ", with_params='" + with_params + '\'' +
                ", select_query='" + select_query + '\'' +
                ", select_query_count='" + select_query_count + '\'' +
                '}';
    }


    public SelectQuery fromMap(Map<String, Object> map, SelectQuery selectQuery) {
        if (map.get("uuid") instanceof String) {
            selectQuery.uuid = (String) map.get("uuid");
        }
        if (map.get("query_name") instanceof String) {
            selectQuery.query_name = (String) map.get("query_name");
        }
        if (map.get("with_params") instanceof Boolean) {
            selectQuery.with_params = (Boolean) map.get("with_params");
        }

        if (map.get("select_query") instanceof String) {
            selectQuery.select_query = (String) map.get("select_query");
        }
        if (map.get("select_query_count") instanceof String) {
            selectQuery.select_query_count = (String) map.get("select_query_count");
        }
        return selectQuery;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new HashMap<>();


        return metadata;
    }

}
