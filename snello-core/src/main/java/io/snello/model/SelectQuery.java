package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

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
        fromMap(map, this);
    }

    public SelectQuery fromMap(Map<String, Object> map, SelectQuery selectQuery) {
        if (map == null || selectQuery == null) {
            return selectQuery;
        }
        selectQuery.uuid = MapReads.str(map, "uuid");
        selectQuery.query_name = MapReads.str(map, "query_name");
        selectQuery.select_query = MapReads.str(map, "select_query");
        selectQuery.select_query_count = MapReads.str(map, "select_query_count");
        selectQuery.with_params = MapReads.bool(map, "with_params", false);
        return selectQuery;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "query_name", query_name);
        MapWrites.put(map, "select_query", select_query);
        MapWrites.put(map, "select_query_count", select_query_count);
        MapWrites.putBool(map, "with_params", with_params);
        return map;
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
}
