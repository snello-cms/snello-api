package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.Map;

@RegisterForReflection
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
        fromMap(map, this);
    }

    public Condition fromMap(Map<String, Object> map, Condition condition) {
        if (map == null || condition == null) {
            return condition;
        }
        condition.uuid = MapReads.str(map, "uuid");
        condition.metadata_uuid = MapReads.str(map, "metadata_uuid");
        condition.metadata_name = MapReads.str(map, "metadata_name");
        condition.separator = MapReads.str(map, "separator");
        condition.query_params = MapReads.str(map, "query_params");
        condition.condition = MapReads.str(map, "condition");
        condition.sub_query = MapReads.str(map, "sub_query");
        condition.metadata_multijoin_uuid = MapReads.str(map, "metadata_multijoin_uuid");
        return condition;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "metadata_uuid", metadata_uuid);
        MapWrites.put(map, "metadata_name", metadata_name);
        MapWrites.put(map, "separator", separator);
        MapWrites.put(map, "query_params", query_params);
        MapWrites.put(map, "condition", condition);
        MapWrites.put(map, "sub_query", sub_query);
        MapWrites.put(map, "metadata_multijoin_uuid", metadata_multijoin_uuid);
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
